package org.ensodai.avalonmediacard.tmdb

import org.ensodai.avalonmediacard.contract.slot.EpisodeItem
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Статус актуальности закешированных данных сезона сериала.
 */
enum class FreshnessStatus {
    /**
     * Кэш полностью актуален. Внешние сетевые запросы не требуются.
     */
    FRESH,

    /**
     * Кэш устарел или содержит временные заглушки (например, непереведенные названия "Эпизод N").
     * Данные из БД можно отдать клиенту немедленно (паттерн SWR), параллельно запустив фоновое обновление.
     */
    STALE_REVALIDATE,

    /**
     * Кэш отсутствует или пуст. Требуется синхронная загрузка с внешнего источника перед отдачей клиенту.
     */
    EXPIRED
}

/**
 * Доменный сервис оценки актуальности кэша сезонов и серий сериалов.
 *
 * Инкапсулирует эвристики жизненного цикла эпизодов:
 * 1. **Детекция заглушек TMDB**: выявление непереведенных названий ("Эпизод 8", "Episode 1")
 *    и отсутствующих постеров у вышедших серий. При наличии заглушек опрос TMDB ускоряется до [PLACEHOLDER_RECHECK_INTERVAL].
 * 2. **Горячее окно (Hot Window)**: активный мониторинг серий, выходящих в диапазоне ±2 дней от текущей даты.
 *    Интервал проверки — [HOT_WINDOW_RECHECK_INTERVAL].
 * 3. **Активный онгоинг**: сезон с выходящими в будущем сериями либо сериями за последние 30 дней.
 *    Интервал проверки — [ACTIVE_ONGOING_RECHECK_INTERVAL].
 * 4. **Архивный сезон**: завершенные сезоны, все серии которых вышли более месяца назад.
 *    Интервал проверки — [ARCHIVE_RECHECK_INTERVAL].
 */
@Single
class SeasonFreshnessPolicy {

    companion object {
        /**
         * Регулярное выражение для обнаружения дефолтных автосгенерированных заглушек TMDB.
         */
        private val PLACEHOLDER_TITLE_REGEX = Regex("^(?:Эпизод|Episode|Серия)\\s+\\d+$", RegexOption.IGNORE_CASE)

        /** Интервал повторной проверки серий с заглушками названий или недавних серий без кадров (15 минут). */
        val PLACEHOLDER_RECHECK_INTERVAL = 15.minutes

        /** Интервал повторной проверки серий в горячем окне премьеры (30 минут). */
        val HOT_WINDOW_RECHECK_INTERVAL = 30.minutes

        /** Интервал повторной проверки активных транслируемых онгоингов (6 часов). */
        val ACTIVE_ONGOING_RECHECK_INTERVAL = 6.hours

        /** Интервал фоновой актуализации завершенных архивных сезонов (30 дней). */
        val ARCHIVE_RECHECK_INTERVAL = 30.days
    }

    /**
     * Проверяет, является ли заголовок эпизода пустой строкой или дефолтной заглушкой TMDB ("Эпизод N").
     */
    fun isPlaceholderTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return true
        return PLACEHOLDER_TITLE_REGEX.matches(title.trim())
    }

    /**
     * Определяет наличие эпизодов с неполными метаданными в сезоне:
     * - эпизоды с датой премьеры <= сегодня + 3 дня, имеющие заглушку вместо перевода названия;
     * - эпизоды, вышедшие за последние 14 дней, у которых ещё отсутствует превью-кадр ([EpisodeItem.stillUrl]).
     */
    fun hasPlaceholders(episodes: List<EpisodeItem>, now: Instant): Boolean {
        val todayPlus3DaysStr = (now + 3.days).toString().substringBefore("T")
        val todayMinus14DaysStr = (now - 14.days).toString().substringBefore("T")

        return episodes.any { ep ->
            val airDate = ep.airDate
            val isRelevantDate = airDate == null || airDate <= todayPlus3DaysStr
            val isPlaceholder = isPlaceholderTitle(ep.name)
            val isMissingRecentStill = ep.stillUrl.isNullOrBlank() &&
                    airDate != null &&
                    airDate >= todayMinus14DaysStr &&
                    airDate <= todayPlus3DaysStr

            (isRelevantDate && isPlaceholder) || isMissingRecentStill
        }
    }

    /**
     * Проверяет, находится ли сезон в «горячем окне» премьеры (есть серии с датой выхода в пределах [now - 2 дня, now + 2 дня]).
     */
    fun isInHotWindow(episodes: List<EpisodeItem>, now: Instant): Boolean {
        val hotWindowStart = (now - 2.days).toString().substringBefore("T")
        val hotWindowEnd = (now + 2.days).toString().substringBefore("T")

        return episodes.any { ep ->
            val airDate = ep.airDate ?: return@any false
            airDate in hotWindowStart..hotWindowEnd
        }
    }

    /**
     * Проверяет, является ли сезон активно выходящим онгоингом:
     * - количество закешированных серий меньше ожидаемого общего числа серий сезона;
     * - либо присутствуют серии с датой выхода в будущем или за последние 30 дней.
     */
    fun isActiveOngoing(episodes: List<EpisodeItem>, now: Instant, expectedCount: Int?): Boolean {
        val todayPlus2Days = (now + 2.days).toString().substringBefore("T")
        val todayMinus30Days = (now - 30.days).toString().substringBefore("T")

        if (expectedCount != null && expectedCount > 0 && episodes.size < expectedCount) {
            return true
        }

        return episodes.any { ep ->
            val airDate = ep.airDate ?: return@any false
            airDate > todayPlus2Days || airDate >= todayMinus30Days
        }
    }

    /**
     * Вычисляет статус свежести сезона на основе текущего состояния кэша, времени последнего обновления и даты.
     *
     * @param episodes Список эпизодов из локальной БД (null или пустой означает отсутствие кэша).
     * @param lastUpdatedAt Временная метка последнего обновления сезона в БД.
     * @param expectedEpisodeCount Ожидаемое общее количество эпизодов в сезоне из метаданных сериала.
     * @param now Текущий момент времени (по умолчанию системное время).
     * @return [FreshnessStatus] для принятия решения каталогом (отдать сразу, запустить фон или ждать сеть).
     */
    fun evaluate(
        episodes: List<EpisodeItem>?,
        lastUpdatedAt: Instant?,
        expectedEpisodeCount: Int? = null,
        now: Instant = Clock.System.now()
    ): FreshnessStatus {
        if (episodes.isNullOrEmpty()) {
            return FreshnessStatus.EXPIRED
        }

        val elapsed = if (lastUpdatedAt != null) now - lastUpdatedAt else null

        // 1. Проверка наличия заглушек (минимальный TTL для быстрого подтягивания появившихся переводов)
        if (hasPlaceholders(episodes, now)) {
            return if (elapsed == null || elapsed >= PLACEHOLDER_RECHECK_INTERVAL) {
                FreshnessStatus.STALE_REVALIDATE
            } else {
                FreshnessStatus.FRESH
            }
        }

        // 2. Горячее окно премьеры (серии выходят на днях)
        if (isInHotWindow(episodes, now)) {
            return if (elapsed == null || elapsed >= HOT_WINDOW_RECHECK_INTERVAL) {
                FreshnessStatus.STALE_REVALIDATE
            } else {
                FreshnessStatus.FRESH
            }
        }

        // 3. Активно транслируемый онгоинг
        if (isActiveOngoing(episodes, now, expectedEpisodeCount)) {
            return if (elapsed == null || elapsed >= ACTIVE_ONGOING_RECHECK_INTERVAL) {
                FreshnessStatus.STALE_REVALIDATE
            } else {
                FreshnessStatus.FRESH
            }
        }

        // 4. Архивный завершенный сезон
        return if (elapsed == null || elapsed >= ARCHIVE_RECHECK_INTERVAL) {
            FreshnessStatus.STALE_REVALIDATE
        } else {
            FreshnessStatus.FRESH
        }
    }
}
