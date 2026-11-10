#!/usr/bin/env python3
"""Record LWJGL 2's GLX selection and ID lookup without creating a GL context."""
import ctypes as c


def function(library, name, result, *arguments):
    value = getattr(library, name)
    value.restype = result
    value.argtypes = list(arguments)
    return value


x11 = c.CDLL("libX11.so.6")
gl = c.CDLL("libGL.so.1")
pointer = c.c_void_p
integer_pointer = c.POINTER(c.c_int)
config_pointer = c.POINTER(pointer)
open_display = function(x11, "XOpenDisplay", pointer, c.c_char_p)
close_display = function(x11, "XCloseDisplay", c.c_int, pointer)
default_screen = function(x11, "XDefaultScreen", c.c_int, pointer)
free = function(x11, "XFree", c.c_int, pointer)
choose = function(gl, "glXChooseFBConfig", config_pointer,
                  pointer, c.c_int, integer_pointer, integer_pointer)
get_all = function(gl, "glXGetFBConfigs", config_pointer,
                   pointer, c.c_int, integer_pointer)
get_attribute = function(gl, "glXGetFBConfigAttrib", c.c_int,
                         pointer, pointer, c.c_int, integer_pointer)

CONFIG_ID = 0x8013
DRAWABLE_TYPE = 0x8010
RENDER_TYPE = 0x8011
RGBA_BIT = 1
PBUFFER_BIT = 4
attributes = {"id": CONFIG_ID, "drawable": DRAWABLE_TYPE, "render": RENDER_TYPE,
              "red": 8, "green": 9, "blue": 10, "alpha": 11,
              "depth": 12, "stencil": 13, "doublebuffer": 5, "visual": 0x800B}
display = open_display(None)
if not display:
    raise RuntimeError("XOpenDisplay failed; run under Xvfb")


def attribute(config, name):
    result = c.c_int()
    error = get_attribute(display, config, attributes[name], c.byref(result))
    if error:
        raise RuntimeError("glXGetFBConfigAttrib failed: %s (%s)" % (name, error))
    return result.value


try:
    screen = default_screen(display)
    total = c.c_int()
    all_configs = get_all(display, screen, c.byref(total))
    try:
        all_ids = [attribute(all_configs[index], "id") for index in range(total.value)]
    finally:
        if all_configs:
            free(all_configs)
    print("GLX screen=%s, enumerated configs=%s" % (screen, total.value), flush=True)
    for label, color_bits, alpha_bits in (("previous unspecified color", 0, 0),
                                           ("explicit RGBA8", 8, 8)):
        # Same non-MSAA/non-accumulation request as LWJGL's Pbuffer selection.
        values = [RENDER_TYPE, RGBA_BIT, 5, 0, DRAWABLE_TYPE, PBUFFER_BIT,
                  12, 24, 8, color_bits, 9, color_bits, 10, color_bits,
                  11, alpha_bits, 13, 8, 7, 0, 14, 0, 15, 0, 16, 0, 17, 0, 0]
        count = c.c_int()
        selected = choose(display, screen, (c.c_int * len(values))(*values), c.byref(count))
        try:
            print("%s: matches=%s" % (label, count.value), flush=True)
            if not selected or count.value == 0:
                continue
            details = {name: attribute(selected[0], name) for name in attributes}
            print("  first=%s" % details, flush=True)
            lookup_count = c.c_int()
            lookup_values = (c.c_int * 3)(CONFIG_ID, details["id"], 0)
            lookup = choose(display, screen, lookup_values, c.byref(lookup_count))
            try:
                print("  ID-only ChooseFBConfig matches=%s; enumerated ID matches=%s"
                      % (lookup_count.value, all_ids.count(details["id"])), flush=True)
            finally:
                if lookup:
                    free(lookup)
        finally:
            if selected:
                free(selected)
finally:
    close_display(display)
