#!/usr/bin/env bash
set -e

# Обеспечиваем существование папок монтирования
mkdir -p /app/data /app/plugins

# Если папка плагинов пуста (например, при первом монтировании тома с хоста),
# инициализируем ее базовыми плагинами из контейнера
if [ -d /app/default-plugins ] && [ -z "$(ls -A /app/plugins 2>/dev/null)" ]; then
    echo "🧩 [Avalon Entrypoint] Initializing default plugins in /app/plugins..."
    cp -r /app/default-plugins/* /app/plugins/ 2>/dev/null || true
fi

# Удаляем устаревший collaps-plugin, если остался от предыдущих запусков
rm -f /app/plugins/collaps-plugin.jar /app/plugins/*collaps*.jar 2>/dev/null || true

exec "$@"
