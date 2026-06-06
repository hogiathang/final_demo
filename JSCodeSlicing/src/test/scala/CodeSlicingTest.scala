import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source
import scala.util.Using
import FileProcessor.IOFileProcessorImpl

/**
 * Integration test suite for JavaScript code slicing tool based on Joern data-flow analysis.
 * 
 * This tool slices code starting from SENSITIVE APIs as defined in academic security research.
 * 
 * IMPORTANT:
 * - All test cases MUST include sensitive APIs (fs.readFileSync, child_process.exec, eval, etc.)
 * - Tests execute the REAL slicing pipeline using actual components (NO mocks, NO stubs)
 * - Each test corresponds to ONE input package with realistic malicious/risky JS patterns
 * - Tests validate that:
 *   1. Output files are generated
 *   2. At least one slice is produced
 *   3. Sensitive APIs appear in the slicing results
 * 
 * Test failures occur if:
 * - No sensitive API is detected
 * - Slicing produces empty results
 */
class CodeSlicingIntegrationTest extends AnyFunSuite with Matchers with BeforeAndAfterEach {
  
  private val baseDir = "src/test/resources"
  private val inputDir = s"$baseDir/input"
  private val outputDir = s"$baseDir/output"
  private val checkpointFile = s"$baseDir/test_checkpoint.txt"
  private val processingFile = s"$baseDir/test_processing.txt"
  private val errorLogFile = s"$baseDir/test_error.log"
  private val timeoutLogFile = s"$baseDir/test_timeout.log"
  
  // Sensitive APIs that should appear in test results
  private val sensitiveApis = Set(
    "readFileSync", "writeFileSync", "readFile", "writeFile",
    "exec", "execSync", "spawn", "fork",
    "eval", "Function",
    "request", "connect",
    "createCipheriv", "createDecipheriv",
    "process.env", "process.argv",
    "fs.readFileSync", "fs.writeFileSync",
    "child_process.exec", "child_process.execSync",
    "child_process.spawn", "child_process.fork",
    "global.eval", "global.Function"
  )
  
  override def beforeEach(): Unit = {
    // Ensure output directory exists
    val outputPath = Paths.get(outputDir)
    if (!Files.exists(outputPath)) {
      Files.createDirectories(outputPath)
    }
  }
  
  override def afterEach(): Unit = {
    // Clean up test state files after each test
    cleanupFile(checkpointFile)
    cleanupFile(processingFile)
  }
  
  private def cleanupFile(filePath: String): Unit = {
    val file = new File(filePath)
    if (file.exists()) {
      file.delete()
    }
  }
  
  private def deleteDirectory(dir: File): Unit = {
    if (dir.exists()) {
      if (dir.isDirectory) {
        dir.listFiles().foreach(deleteDirectory)
      }
      dir.delete()
    }
  }
  
  /**
   * Helper method to run slicing test for a specific package.
   * 
   * This method:
   * 1. Cleans the output directory for this package
   * 2. Verifies input package exists
   * 3. Invokes Main.processPackage with real components
   * 4. Validates that output files are generated
   * 5. Asserts that at least one slice is produced
   * 6. Asserts that a sensitive API appears in the slice results
   * 
   * @param packageName The name of the package to test (e.g., "pkg_command_exec")
   */
  private def runSlicingTest(packageName: String): Unit = {
    val packagePath = s"$inputDir/$packageName"
    val packageOutputDir = s"$outputDir/$packageName"
    
    // Clean output directory before test
    val outputPackageDir = new File(packageOutputDir)
    if (outputPackageDir.exists()) {
      deleteDirectory(outputPackageDir)
    }
    
    // Verify input package exists
    val inputPackageDir = new File(packagePath)
    inputPackageDir.exists() shouldBe true
    inputPackageDir.isDirectory shouldBe true
    
    Main.processPackage(
      packagePath = packagePath,
      outputFolder = outputDir,
      ioFileProcessor = IOFileProcessorImpl,
      checkpointFilePath = checkpointFile,
      isProcessingFilePath = processingFile,
      errorLogFilePath = errorLogFile,
      timeoutLogFilePath = timeoutLogFile
    )
    
    // Validate output was generated
    outputPackageDir.exists() shouldBe true
    outputPackageDir.isDirectory shouldBe true
    
    // Verify output directory contains files (slicing results)
    val outputFiles = outputPackageDir.listFiles()
    outputFiles should not be null
    
    // CRITICAL: Tests must fail if slicing produces empty results
    withClue(s"Slicing produced no output files for $packageName") {
      outputFiles.length should be > 0
    }
    
    // Read output files and check for sensitive APIs
    var foundSensitiveApi = false
    var sliceCount = 0
    
    outputFiles.foreach { file =>
      if (file.isFile && file.getName.endsWith(".txt")) {
        Using(Source.fromFile(file)) { source =>
          val content = source.mkString
          sliceCount += 1
          
          // Check if any sensitive API appears in the slice
          if (sensitiveApis.exists(api => content.contains(api))) {
            foundSensitiveApi = true
          }
        }
      }
    }
    
    // // CRITICAL: Tests must fail if no sensitive API is detected
    withClue(s"No sensitive API found in slicing results for $packageName") {
      foundSensitiveApi shouldBe true
    }
    
    // Verify at least one slice was generated
    withClue(s"No slices generated for $packageName") {
      sliceCount should be > 0
    }
    
    println(s"✓ Successfully sliced $packageName - generated $sliceCount slices with sensitive APIs")
  }

  test("gambit-interface-1.0.0") {
    runSlicingTest("gambit-interface-1.0.0")
  }
}