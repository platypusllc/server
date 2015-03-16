#!/bin/bash
#
# This script builds and installs MADARA into the libs directory of
# this application.  In order to run it, first download and install the Android NDK.
# (https://developer.android.com/tools/sdk/ndk/index.html#Installing)
#
# Save its path to the environment variable 'NDK':
#
# $ export NDK=<path to ndk>
#
# Save its path to the environment variable 'NDK_BIN':
#
# $ export NDK_BIN=<path_to_ndk_standalone_toolchain>
#
# Then, execute this script.
#
# $ ./build_madara.sh
#

# Store the script directory.
SCRIPT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

# Return an error if the NDK path was not set.
if [ -z "$NDK" ]
then
    echo "ERROR: path to NDK was not set.  Please do the following:"
    echo "    $ export NDK=<Path to Android NDK>"
    exit 1
fi

# Set up temporary install location.
BUILD_ROOT="/tmp/madara"
rm -rf "${BUILD_ROOT}"
mkdir -p "${BUILD_ROOT}"

# Build the standalone NDK toolchain.
echo "Building standalone NDK toolchain..."
export NDK_TOOLCHAIN="${BUILD_ROOT}/ndk_toolchain"
export NDK_BIN="${NDK_TOOLCHAIN}/bin"
pushd "${NDK}"
./build/tools/make-standalone-toolchain.sh \
	--toolchain=arm-linux-androideabi-4.8 \
	--arch=arm --platform=android-14 \
	--install-dir="${NDK_TOOLCHAIN}"
if [ $? -ne 0 ]
then
    echo "ERROR: Unable to build standalone NDK toolchain."
    exit 2
fi
popd

# Define necessary ACE and MADARA build directories.
export ACE_ROOT=$BUILD_ROOT/ace/ACE_wrappers
export MADARA_ROOT=$BUILD_ROOT/madara
export LD_LIBRARY_PATH=$ACE_ROOT/lib:$MADARA_ROOT/lib:$LD_LIBRARY_PATH
export PATH=$ACE_ROOT/bin:$MADARA_ROOT/bin:$PATH
export PATH=$NDK_BIN:$PATH

# Use all available cores.
CORES=$(nproc)

# Make the ACE and MADARA directories in $BUILD_ROOT
rm -rf $BUILD_ROOT/ace $BUILD_ROOT/madara
mkdir -p $BUILD_ROOT/ace $BUILD_ROOT/madara

# Download and install ACE.
echo "Downloading ACE..."
svn co -q svn://svn.dre.vanderbilt.edu/DOC/Middleware/sets-anon/ACE $BUILD_ROOT/ace
cd $ACE_ROOT/ace
echo "#include \"ace/config-android.h\"" > config.h
echo "#define ACE_HAS_UCONTEXT_T" >> config.h
cd $ACE_ROOT/include/makeinclude
echo -e "versioned_so=0\nstatic_libs_only=0\ninclude \$(ACE_ROOT)/include/makeinclude/platform_android.GNU" > platform_macros.GNU
cd $ACE_ROOT/ace
echo "Building ACE..."
perl $ACE_ROOT/bin/mwc.pl -type gnuace ace.mwc
make -j $CORES
if [ $? -ne 0 ]
then
    echo "ERROR: Unable to build ACE."
    exit 3
fi

# Download and install MADARA.
echo "Downloading MADARA..."
git clone git://git.code.sf.net/p/madara/code $BUILD_ROOT/madara
cd $MADARA_ROOT
git apply "${SCRIPT_DIR}/madara_ndk.patch"
if [ $? -ne 0 ]
then
    echo "ERROR: Unable to patch MADARA against latest NDK."
    exit 4
fi
echo "Building MADARA..."
perl $ACE_ROOT/bin/mwc.pl -type gnuace -features java=1,android=1,tests=0 MADARA.mwc
make tests=0 java=1 android=1 -j $CORES
if [ $? -ne 0 ]
then
    echo "ERROR: Unable to build MADARA."
    exit 5
fi

# Copy the resulting build files to the appropriate library directory for
# native libraries. Based on http://stackoverflow.com/a/22072984
mkdir -p "${SCRIPT_DIR}/libs/armeabi"
arm-linux-androideabi-strip "${MADARA_ROOT}/lib/libMADARA.so"
cp "${MADARA_ROOT}/lib/libMADARA.so" "${SCRIPT_DIR}/libs/armeabi/"
cp "${MADARA_ROOT}/lib/madara.jar" "${SCRIPT_DIR}/libs/"
echo "Done building MADARA for Android."

# Remove temporary files.
rm -rf "${BUILD_ROOT}"
