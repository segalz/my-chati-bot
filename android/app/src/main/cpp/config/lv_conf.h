/**
 * LVGL configuration for the Android port of the Monochrome Blue eyes.
 *
 * Only what differs from LVGL's defaults (src/lv_conf_internal.h at the pinned commit) is set.
 * The eyes only need the canvas widget and the software renderer; everything runs on one
 * native render thread, so no OS integration is used.
 */
#ifndef LV_CONF_H
#define LV_CONF_H

/* RGB565, the format the upstream example uses for its canvas. */
#define LV_COLOR_DEPTH 16

/* Single render thread owns all LVGL calls. */
#define LV_USE_OS LV_OS_NONE

/* Plain C library allocator instead of LVGL's fixed memory pool. */
#define LV_USE_STDLIB_MALLOC  LV_STDLIB_CLIB
#define LV_USE_STDLIB_STRING  LV_STDLIB_CLIB
#define LV_USE_STDLIB_SPRINTF LV_STDLIB_CLIB

/* Logging goes to logcat through lv_log_register_print_cb() in the host. */
#define LV_USE_LOG 1
#define LV_LOG_LEVEL LV_LOG_LEVEL_WARN
#define LV_LOG_PRINTF 0

/* Fail loudly instead of spinning forever (LVGL's default handler is `while(1);`). */
#define LV_ASSERT_HANDLER_INCLUDE <stdlib.h>
#define LV_ASSERT_HANDLER abort();

#endif /* LV_CONF_H */
