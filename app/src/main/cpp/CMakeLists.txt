cmake_minimum_required(VERSION 3.22)
project(panelgrove_native LANGUAGES C CXX)
include(FetchContent)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(feature OPENSSL MD LIBB2 LZ4 LZO LZMA ZSTD BZip2 LIBXML2 BSDXML EXPAT WIN32_XMLLITE
    PCREPOSIX PCRE2POSIX CNG TAR CPIO CAT UNZIP XATTR ACL ICONV TEST INSTALL WERROR)
    set(ENABLE_${feature} OFF CACHE BOOL "" FORCE)
endforeach()
set(ENABLE_ZLIB ON CACHE BOOL "" FORCE)
# Build the pinned codec locally; never pick up a host liblzma while cross-compiling.
foreach(feature NLS TOOL_XZ TOOL_XZDEC TOOL_LZMADEC TOOL_LZMAINFO TOOL_SCRIPTS DOC)
    set(XZ_${feature} OFF CACHE BOOL "" FORCE)
endforeach()
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(XZ_THREADS no CACHE STRING "" FORCE)
FetchContent_Declare(xz
    URL "${CMAKE_CURRENT_SOURCE_DIR}/../../../../gradle/xz-5.8.4.tar.xz"
    URL_HASH SHA256=4ce24038fd4221e0d13bc1a2de7a4db56e90b92b3bf75321f6c14be73f65de4b)
FetchContent_MakeAvailable(xz)
FetchContent_Declare(libarchive
    URL "${CMAKE_CURRENT_SOURCE_DIR}/../../../../gradle/libarchive-3.8.10.tar.xz"
    URL_HASH SHA256=56cad765459d5ee25a3b761f90c47d99379d28297fbfb72d908880d6b875fb96)
FetchContent_MakeAvailable(libarchive)
# libarchive's system-library discovery is disabled above. Its 7Z reader uses
# the vendored codec through these feature definitions and target dependency.
target_compile_definitions(archive_static PRIVATE HAVE_LIBLZMA=1 HAVE_LZMA_H=1)
target_include_directories(archive_static PRIVATE ${xz_SOURCE_DIR}/src/liblzma/api)
target_link_libraries(archive_static liblzma)
add_library(panelgrove_archive SHARED archive_bridge.cpp)
target_compile_features(panelgrove_archive PRIVATE cxx_std_17)
target_include_directories(panelgrove_archive PRIVATE ${libarchive_SOURCE_DIR}/libarchive ${libarchive_BINARY_DIR})
target_link_libraries(panelgrove_archive PRIVATE archive_static z)
target_link_options(panelgrove_archive PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
