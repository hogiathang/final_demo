import FileProcessor.*
import scala.concurrent.*
import scala.concurrent.duration.*
import java.util.concurrent.{Executors, TimeoutException}
import scala.concurrent.ExecutionContext
// import CodeSlice.CodeSliceImp
import scala.util.{Success, Failure}
import CodeSlice.JoernDataFlowAnalysis

object Main {
  // Set number of parallel processes

  private implicit val executionContext: ExecutionContext = 
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())


  /*
    * Main Function of Code Slicing Application
    * Args:
    *   args(0): Input folder path containing code packages
    *   args(1): Output folder path to save results
    * Returns: Unit

   */
  def main(args: Array[String]): Unit = {
    // Clear console
    print("\u001b[H\u001b[2J")

    if (args.length < 2) {
      println("Usage: sbt \"run <input_folder> <output_folder>\"")
      sys.exit(1)
    }

    var ioFileProcessor: IOFileProcessor = IOFileProcessorImpl
    val inputFolder = args(0)
    val outputFolder = args(1)
    val PARALLELISM = args.lift(2).getOrElse("1").toInt
    val checkpointFilePath = args.lift(3).getOrElse("./src/main/resources/checkpoint.txt")
    val isProcessingFilePath = args.lift(4).getOrElse("./src/main/resources/is_processing.txt")
    val errorLogFilePath = args.lift(5).getOrElse("./src/main/resources/error_log.txt")
    val timeoutLogFilePath = args.lift(6).getOrElse("./src/main/resources/timeout_log.txt")


    println(s"Input: $inputFolder")
    println(s"Output: $outputFolder")
    println(s"Parallelism Level: $PARALLELISM")

    val allPackageDirectories = ioFileProcessor.listPackageDirectories(inputFolder)
    println(s"Total packages found: ${allPackageDirectories.size}")

    // Tiến hành xây dựng parellel processing với số process là PARALLELISM
    val executorService = Executors.newFixedThreadPool(PARALLELISM)

    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executorService)

    val futures = allPackageDirectories.map(packagePath => Future {
      processPackage(
        packagePath, outputFolder, ioFileProcessor,
        checkpointFilePath, isProcessingFilePath, 
        errorLogFilePath, timeoutLogFilePath
      )
    }.recover({
      case e: Throwable =>
        println(s"[FUTURE ERROR] ${ioFileProcessor.getPackageName(packagePath)}: ${e.getMessage}")
    }))

    Await.ready(Future.sequence(futures), Duration.Inf)

    executorService.shutdown()
    System.gc()

    println("\nAll Processing Complete!")
    System.exit(0)
  }

  /*
    * Process a single code package for data flow analysis and slicing.
    * 
    * Args:
    *   packagePath: Path to the code package
    *   outputFolder: Folder to save output results
    *   ioFileProcessor: IOFileProcessor instance for file operations
    *   checkpointFilePath: Path to checkpoint file
    *   isProcessingFilePath: Path to processing status file
    *   errorLogFilePath: Path to error log file
    *   timeoutLogFilePath: Path to timeout log file
    * 
    * Returns: Unit 
  */
  def processPackage(
      packagePath: String, 
      outputFolder: String,
      ioFileProcessor: IOFileProcessor,
      checkpointFilePath: String,
      isProcessingFilePath: String,
      errorLogFilePath: String,
      timeoutLogFilePath: String
  ): Unit = {
    
    val packageName = ioFileProcessor.getPackageName(packagePath)
    
    val processedPackages = ioFileProcessor.loadCheckpoint(checkpointFilePath)
    val processingPackages = ioFileProcessor.loadProcessing(isProcessingFilePath)

    if (ioFileProcessor.isPackageProcessed(packageName, processedPackages)) {
       println(s"[SKIP] Done: $packageName")
       return
    }
    
    if (processingPackages.contains(packageName)) {
       println(s"[SKIP] Busy: $packageName")
       return
    }

    // Checkpoint to save progress
    isProcessingFilePath.synchronized { ioFileProcessor.addToProcessing(isProcessingFilePath, packageName) }

    val startTime = System.currentTimeMillis()
    val TIMEOUT_SECONDS = 180
    var analyzer: JoernDataFlowAnalysis = null

    try {
      val task = Future {
        analyzer = new JoernDataFlowAnalysis(
            inputDir = packagePath,
            outputDir = s"$outputFolder/$packageName/"
        )

        analyzer.runDataFlowAnalysis(ioFileProcessor, packageName)
        analyzer.close()
        analyzer = null
      }

      // Wait for completion with timeout
      // Await.result(task, TIMEOUT_SECONDS.seconds)
      Await.result(task, Duration.Inf)
      val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
      println(s"[OK] $packageName (${elapsedTime}s)")

    } catch {
      case _: TimeoutException =>
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        println(s"[TIMEOUT] $packageName (> ${TIMEOUT_SECONDS}s)")
        
        timeoutLogFilePath.synchronized {
            ioFileProcessor.saveOutputPackage(timeoutLogFilePath, s"$packageName,${elapsedTime}s\n")
        }
        
        // Clean up and force GC to prevent memory issues
        System.gc()

      // Catch OOM and other errors
      case e: Throwable =>
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
        val isOOM = e.isInstanceOf[OutOfMemoryError]
        val errType = if (isOOM) "OOM_CRASH" else "ERROR"
        
        println(s"[$errType] $packageName: ${e.getMessage}")
        
        // Log the error
        errorLogFilePath.synchronized {
            ioFileProcessor.saveOutputPackage(errorLogFilePath, s"$packageName,${elapsedTime}s,${e.toString}\n")
        }
        
        // Save partial results if have
        if (analyzer != null) {
            try { analyzer.close() } catch { case _: Throwable => }
            analyzer = null
        }
        if (isOOM) {
            System.gc() // Force garbage collection on OOM
        }
    } finally {
      // Remove from processing list
      isProcessingFilePath.synchronized {
        ioFileProcessor.removeFromProcessing(isProcessingFilePath, packageName)
      }

      checkpointFilePath.synchronized { 
        ioFileProcessor.saveCheckpoint(checkpointFilePath, packageName) 
      }

      // ioFileProcessor.removeFile(s"$packagePath/cpg.bin")
    }
  }
}
