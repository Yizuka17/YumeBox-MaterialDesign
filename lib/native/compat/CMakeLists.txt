cmake_minimum_required(VERSION 3.22.1)

project(compat C)

add_library(compat SHARED libcompat.c)
target_compile_features(compat PRIVATE c_std_17)
target_compile_options(compat PRIVATE -O2 -fvisibility=hidden -Wall -Wextra -Werror)
target_link_options(compat PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-soname,libcompat.so)
