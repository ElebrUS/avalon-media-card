#!/usr/bin/env bash
set -e

# Обеспечиваем существование папок монтирования
mkdir -p /app/data /app/plugins

# Синхронизируем базовые плагины из образа в /app/plugins (overwrite).
# Кастомные JAR, которых нет в default-plugins, не трогаем.
if [ -d /app/default-plugins ] && [ -n "$(ls -A /app/default-plugins 2>/dev/null)" ]; then
    echo "🧩 [Avalon Entrypoint] Syncing default plugins into /app/plugins..."
    cp -f /app/default-plugins/* /app/plugins/ 2>/dev/null || true
fi

# Удаляем устаревший collaps-plugin, если остался от предыдущих запусков
rm -f /app/plugins/collaps-plugin.jar /app/plugins/*collaps*.jar 2>/dev/null || true

exec "$@"
