<#
    Builds the MouseXButtonFix mod for Minecraft 1.8.9 and 1.12.2.

    The mod does not need Minecraft or Gradle: it only compiles against the Forge
    universal jar, LWJGL and JNA, all of which are downloaded into .\libs on first use.
#>
[CmdletBinding()]
param(
    # JDK to compile with. Defaults to the newest installed JDK 8, then whatever
    # javac is on the PATH (compiled with --release 8 in that case).
    [string]$JavaHome = '',

    # Also compile and run the unit tests.
    [switch]$Test
)

# 'Continue' because javac and jar write warnings to stderr; the exit code of every
# external tool is checked explicitly instead.
$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$libs = Join-Path $root 'libs'
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist'
$version = '1.0.0'

# ---------------------------------------------------------------------------
# Dependencies
# ---------------------------------------------------------------------------

$dependencies = @(
    @{ File = 'lwjgl.jar'; Url = 'https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar' },
    @{ File = 'forge-1.8.9-universal.jar'; Url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.8.9-11.15.1.2318-1.8.9/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar' },
    @{ File = 'forge-1.12.2-universal.jar'; Url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar' },
    @{ File = 'jna-3.4.0.jar'; Url = 'https://libraries.minecraft.net/net/java/dev/jna/jna/3.4.0/jna-3.4.0.jar' },
    @{ File = 'jna-4.4.0.jar'; Url = 'https://libraries.minecraft.net/net/java/dev/jna/jna/4.4.0/jna-4.4.0.jar' }
)

New-Item -ItemType Directory -Force -Path $libs | Out-Null
foreach ($dependency in $dependencies) {
    $path = Join-Path $libs $dependency.File
    if (-not (Test-Path $path)) {
        Write-Host "Downloading $($dependency.File)"
        & curl.exe -sSL -o $path $dependency.Url
        if ($LASTEXITCODE -ne 0) {
            throw "Could not download $($dependency.Url)"
        }
    }
}

# ---------------------------------------------------------------------------
# Toolchain
# ---------------------------------------------------------------------------

function Find-Jdk8 {
    $bases = @(
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Amazon Corretto',
        'C:\Program Files\Zulu'
    )
    foreach ($base in $bases) {
        if (Test-Path $base) {
            $match = Get-ChildItem $base -Directory |
                Where-Object { $_.Name -match '^jdk-?1?\.?8' } |
                Sort-Object Name -Descending |
                Select-Object -First 1
            if ($match) {
                return $match.FullName
            }
        }
    }
    return $null
}

if ($JavaHome) {
    $jdk = $JavaHome
} else {
    $jdk = Find-Jdk8
}

if ($jdk) {
    $javac = Join-Path $jdk 'bin\javac.exe'
    $jarTool = Join-Path $jdk 'bin\jar.exe'
    $javaTool = Join-Path $jdk 'bin\java.exe'
} else {
    $javacCommand = Get-Command javac -ErrorAction SilentlyContinue
    $jarCommand = Get-Command jar -ErrorAction SilentlyContinue
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javacCommand -or -not $jarCommand) {
        throw 'No JDK found. Pass -JavaHome <path to a JDK>.'
    }
    $javac = $javacCommand.Source
    $jarTool = $jarCommand.Source
    $javaTool = if ($javaCommand) { $javaCommand.Source } else { $null }
}

$releaseArguments = @()
if ($jdk) {
    $releaseFile = Join-Path $jdk 'release'
    $isJava8 = (Test-Path $releaseFile) -and ((Get-Content $releaseFile -Raw) -match 'JAVA_VERSION="1\.8')
    if (-not $isJava8) {
        $releaseArguments = @('--release', '8')
    }
} else {
    $releaseArguments = @('--release', '8')
}
Write-Host "Using $javac"

# ---------------------------------------------------------------------------
# Build one jar per Minecraft version
# ---------------------------------------------------------------------------

$targets = @(
    @{ Minecraft = '1.8.9'; Forge = 'forge-1.8.9-universal.jar'; Jna = 'jna-3.4.0.jar' },
    @{ Minecraft = '1.12.2'; Forge = 'forge-1.12.2-universal.jar'; Jna = 'jna-4.4.0.jar' }
)

New-Item -ItemType Directory -Force -Path $build | Out-Null
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$jarPaths = @()

foreach ($target in $targets) {
    $classes = Join-Path $build "classes-$($target.Minecraft)"
    if (Test-Path $classes) {
        Remove-Item -Recurse -Force $classes
    }
    New-Item -ItemType Directory -Force -Path $classes | Out-Null

    $classpath = @(
        (Join-Path $libs 'lwjgl.jar'),
        (Join-Path $libs $target.Forge),
        (Join-Path $libs $target.Jna)
    ) -join ';'

    $sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName }

    & $javac @releaseArguments -classpath $classpath -d $classes $sources
    if ($LASTEXITCODE -ne 0) {
        throw "Compiling for Minecraft $($target.Minecraft) failed."
    }

    Copy-Item (Join-Path $root "src\main\resources\$($target.Minecraft)\mcmod.info") (Join-Path $classes 'mcmod.info')

    $jarPath = Join-Path $dist "MouseXButtonFix-$version-mc$($target.Minecraft).jar"
    if (Test-Path $jarPath) {
        Remove-Item -Force $jarPath
    }
    & $jarTool cfm $jarPath (Join-Path $root 'src\main\resources\MANIFEST.MF') -C $classes .
    if ($LASTEXITCODE -ne 0) {
        throw "Packaging $jarPath failed."
    }

    Write-Host ("Built {0} ({1:N0} bytes)" -f $jarPath, (Get-Item $jarPath).Length)
    $jarPaths += $jarPath
}

# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

if ($Test) {
    $mainClasses = Join-Path $build 'classes-1.12.2'
    $testClasses = Join-Path $build 'test-classes'
    if (Test-Path $testClasses) {
        Remove-Item -Recurse -Force $testClasses
    }
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null

    $classpath = @(
        $mainClasses,
        (Join-Path $libs 'lwjgl.jar'),
        (Join-Path $libs 'forge-1.12.2-universal.jar'),
        (Join-Path $libs 'jna-4.4.0.jar')
    ) -join ';'

    $sources = Get-ChildItem (Join-Path $root 'tests\java') -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName }

    & $javac @releaseArguments -classpath $classpath -d $testClasses $sources
    if ($LASTEXITCODE -ne 0) {
        throw 'Compiling the tests failed.'
    }

    if (-not $javaTool) {
        throw 'Tests need a java executable; pass -JavaHome <path to a JDK>.'
    }
    & $javaTool -classpath "$testClasses;$classpath" com.mousexbuttonfix.WrapperTest
    if ($LASTEXITCODE -ne 0) {
        throw 'Tests failed.'
    }
}

Write-Host 'Done.'
