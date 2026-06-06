package CodeSlice

import io.shiftleft.codepropertygraph.generated.Cpg
import io.shiftleft.codepropertygraph.generated.nodes.*
import io.shiftleft.semanticcpg.language.*
import io.joern.jssrc2cpg.{Config, JsSrc2Cpg}
import io.joern.x2cpg.X2Cpg
import io.joern.dataflowengineoss.language.*
import io.joern.dataflowengineoss.queryengine.EngineContext
import io.joern.dataflowengineoss.semanticsloader.{FlowSemantic, FullNameSemantics, Semantics}
import io.shiftleft.codepropertygraph.generated.EdgeTypes
import io.joern.dataflowengineoss.layers.dataflows.{OssDataFlow, OssDataFlowOptions}
import io.shiftleft.semanticcpg.layers.LayerCreatorContext
import io.joern.dataflowengineoss.DefaultSemantics

import scala.util.{Failure, Success}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*
import _root_.Type.Source.SourceGroups
import _root_.Type.Sink.SinkGroups
import _root_.Type.{CallType, CodeType, RegexType, TypeDefinition}

import java.util.regex.Pattern
import FileProcessor.IOFileProcessor

import scala.collection.mutable



class JoernDataFlowAnalysisBK(inputDir: String, outputDir: String) {

    val outputDirectory: String = outputDir
    val DEBUG_MODE: Boolean = false
    private val MAX_DEPTH = 20
    
    /**
     * Lazy create CPG when first accessed
     * Lazy evaluation to avoid unnecessary computation
     * @return Cpg object
     */
    lazy val cpg: Cpg = {
        if (DEBUG_MODE) {
            println(s"Generating CPG from: $inputDir")
        }
        
        val config = Config()
            .withInputPath(inputDir)
            .withOutputPath(s"$outputDir/cpg.bin")

        val jsSrc2Cpg = new JsSrc2Cpg()
        val generatedCpg = jsSrc2Cpg.createCpg(config) match {
            case Success(cpg) => cpg
            case Failure(exception) =>
                throw new RuntimeException(s"Failed to create CPG: $exception")
        }

        X2Cpg.applyDefaultOverlays(generatedCpg)
        val context = new LayerCreatorContext(generatedCpg)
        val options = new OssDataFlowOptions()
        new OssDataFlow(options).run(context)
        
        generatedCpg

    }

    /**
     * IMPORTANT: Must having semantics and engineContext in scope for data flow analysis
     */
    val defaultSemanticsList: List[FlowSemantic] = DefaultSemantics().elements

    implicit val semantic: Semantics = FullNameSemantics.fromList(defaultSemanticsList)

    implicit val engineContext: EngineContext = new EngineContext(semantic)

    var sliceCount: Int = 0

    // ---------------------------------------------------------------------------
    // Source file cache: avoid re-reading the same file for every flow element.
    // Key: resolved absolute path. Value: indexed lines.
    // ---------------------------------------------------------------------------
    private val fileLineCache = scala.collection.mutable.Map[String, IndexedSeq[String]]()

    private def cachedLines(absPath: String): IndexedSeq[String] =
        fileLineCache.getOrElseUpdate(absPath, {
            try Files.readAllLines(Paths.get(absPath)).asScala.toIndexedSeq
            catch { case _: Exception => IndexedSeq.empty }
        })

    /**
     * Resolve a CPG-recorded filename (possibly relative) to an absolute path.
     * JsSrc2Cpg stores paths relative to inputDir.
     */
    private def resolveAbsPath(cpgFileName: String): Option[String] = {
        val candidates = Seq(
            cpgFileName,
            s"$inputDir/$cpgFileName",
            s"$inputDir/${Paths.get(cpgFileName).getFileName}"
        )
        candidates.find(p => Files.exists(Paths.get(p)))
    }

    /**
     * Read a specific line from disk (1-based).  Returns None when out-of-range
     * or the file cannot be found.  Never uses CPG .code.
     */
    private def readSourceLine(cpgFileName: String, lineNum: Int): Option[String] =
        resolveAbsPath(cpgFileName).flatMap { abs =>
            val lines = cachedLines(abs)
            if (lineNum >= 1 && lineNum <= lines.length) Some(lines(lineNum - 1))
            else None
        }

    /**
     * Read the full content of a source file.  Returns empty string on failure.
     */
    private def readFullFile(cpgFileName: String): String =
        resolveAbsPath(cpgFileName)
            .map(abs => new String(Files.readAllBytes(Paths.get(abs))))
            .getOrElse("")

    /**
     * Test Function use for Test and Debug
     * @param sourceRegex Regex pattern to identify source functions/variables
     * @param sinkRegex Regex pattern to identify sink functions/variables
     */
    def testSensitiveFlows(sourceRegex: String, sinkRegex: String): Unit = {
        if (DEBUG_MODE) {
            println(s"Scanning flows from '$sourceRegex' to '$sinkRegex'...")
        }

        val sources = cpg.call
                      .where(_.name(sourceRegex))
                      .toSet

        def sinks = cpg.call
                      .where(_.name(sinkRegex))
                      .l

        if (sources.isEmpty) {
            if (DEBUG_MODE) {
                println(s"[WARNING] No sources found for regex: $sourceRegex")
                println("Hints: Check if regex matches 'code' or 'variable names'.")
            }
            return
        }

        val flows = sinks.reachableByFlows(sources).l
        
        if (flows.isEmpty) {
            if (DEBUG_MODE) {
                println("No flows found.")
            }
        } else {
            if (DEBUG_MODE) {
                println(s"Found ${flows.size} flows:")
            }
            flows.foreach { flow => 
                if (DEBUG_MODE) {
                    println("--------------------------------------------------")
                }
                flow.elements.map {
                     case c: Call => s"[Call] ${c.code}"
                     case i: Identifier => s"[Id] ${i.name}"
                     case p: MethodParameterIn => s"[Param] ${p.name}"
                     case other => s"[${other.label}] ${other.code}"
                }.foreach(println)
            }
        }
    }

    private def collectSources(): Map[TypeDefinition, List[Expression]] = {
        SourceGroups.getAllSources.map { sourceGroup =>

            val sources: List[Expression] = sourceGroup match {

            case CallType(name) =>
                cpg.call.nameExact(name)
                .map(_.asInstanceOf[Expression])
                .l

            case RegexType(pattern) =>
                val callSources =
                cpg.call.name(pattern)
                    .map(_.asInstanceOf[Expression])
                    .l

                val idSources =
                cpg.identifier.name(pattern)
                    .map(_.asInstanceOf[Expression])
                    .l

                (callSources ++ idSources).distinct

            case CodeType(codeSnippet) =>
                val pattern = ".*" + Pattern.quote(codeSnippet) + ".*"
                cpg.expression.code(pattern)
                .map(_.asInstanceOf[Expression])
                .l
            }

            sourceGroup -> sources
        }.toMap
    }

    private def collectSinks(): Map[TypeDefinition, List[Expression]] = {
        SinkGroups.getAllSinks.map { sinkGroup =>

            val sinks: List[Expression] = sinkGroup match {

            case CallType(name) =>
                cpg.call.name(s".*$name.*").argument.l

            case RegexType(ptrn) =>
                val callByName =
                cpg.call.filter(c =>
                    c.name.matches(ptrn) ||
                    c.methodFullName.matches(ptrn)
                )

                val callByReceiver =
                cpg.call.where(_.receiver.isIdentifier.name(ptrn))

                val callByCode =
                cpg.call.code(s".*$ptrn.*")

                val idSink =
                cpg.identifier.name(ptrn)

                (
                callByName.argument.l ++
                callByReceiver.argument.l ++
                callByCode.argument.l ++
                idSink.map(_.asInstanceOf[Expression]).l
                ).distinct

            case CodeType(code) =>
                cpg.call.code(s".*$code.*").argument.l
            }

            sinkGroup -> sinks
        }.toMap
    }

    /**
     * Walk AST-parent edges upward to find the nearest enclosing Method node.
     * Uses only raw EdgeTypes.AST edges — no semanticcpg extension methods
     * needed, so it works on any AstNode subtype including plain AstNode.
     */
    private def findEnclosingMethod(node: AstNode): Option[Method] = {
        var current: AstNode = node
        while (current != null) {
            current match {
                case m: Method => return Some(m)
                case _ =>
            }
            val parents = current.in(EdgeTypes.AST)
            if (parents.hasNext)
                current = parents.next().asInstanceOf[AstNode]
            else
                current = null
        }
        None
    }

    private val blockCommentPattern = "(?s)/\\*.*?\\*/".r
    private val lineCommentPattern = "(?m)//.*$".r

    private def stripComments(code: String): String = {
        val noLine = lineCommentPattern.replaceAllIn(code, "")
        val noBlock = blockCommentPattern.replaceAllIn(noLine, "")
        noBlock
          .linesIterator
          .map(_.trim)
          .filter(_.nonEmpty)
          .mkString("\n")
    }

    private def buildFlowSlice(flows: List[Path]): String = {

        val flowNodes: List[AstNode] =
            flows.iterator.flatMap(_.elements).toList.distinct

        val methods: List[Method] =
            flowNodes
              .flatMap(findEnclosingMethod)
              .distinctBy(_.id())

        if (methods.nonEmpty) {

            val builder = new StringBuilder

            methods.foreach { m =>

                val cpgFile = m.filename
                val startLine = m.lineNumber.getOrElse(-1)
                val endLine = m.lineNumberEnd.getOrElse(-1)

                if (startLine > 0 && endLine >= startLine) {

                    resolveAbsPath(cpgFile) match {

                        case Some(abs) =>
                            val raw =
                                cachedLines(abs)
                                  .slice(startLine - 1, endLine)
                                  .mkString("\n")

                            val cleaned = stripComments(raw)
                            if (cleaned.nonEmpty)
                                builder.append(cleaned).append("\n")

                        case None =>
                            flowNodes
                              .filter(n => findEnclosingMethod(n).exists(_.id() == m.id()))
                              .flatMap(n => n.lineNumber.map(l => (cpgFile, l)))
                              .distinct
                              .sortBy(_._2)
                              .foreach { case (fn, ln) =>
                                  readSourceLine(fn, ln)
                                    .map(stripComments)
                                    .filter(_.nonEmpty)
                                    .foreach(builder.append(_).append("\n"))
                              }
                    }

                } else {

                    flowNodes
                      .filter(n => findEnclosingMethod(n).exists(_.id() == m.id()))
                      .flatMap(n => n.lineNumber.map(l => (cpgFile, l)))
                      .distinct
                      .sortBy(_._2)
                      .foreach { case (fn, ln) =>
                          readSourceLine(fn, ln)
                            .map(stripComments)
                            .filter(_.nonEmpty)
                            .foreach(builder.append(_).append("\n"))
                      }
                }
            }

            builder.toString().trim

        } else {

            val lineGroups =
                flowNodes.flatMap { n =>
                      n.lineNumber.flatMap { ln =>
                          val fileNode =
                              n.in(EdgeTypes.AST)
                                .collectFirst { case nb: NamespaceBlock => nb.filename }

                          fileNode.map(fn => (fn, ln))
                      }
                  }
                  .distinct
                  .sortBy(_._2)

            val builder = new StringBuilder

            lineGroups.foreach { case (cpgFile, ln) =>

                val content =
                    readSourceLine(cpgFile, ln)
                      .orElse {
                          val calls = cpg.call.lineNumber(ln).l
                          if (calls.nonEmpty)
                              Some(calls.maxBy(_.code.length).code)
                          else
                              cpg.all.collect { case n: AstNode => n }
                                .filter(_.lineNumber.contains(ln)).l
                                .headOption
                                .map(_.code)
                      }

                content
                  .map(stripComments)
                  .filter(_.nonEmpty)
                  .foreach(builder.append(_).append("\n"))
            }

            builder.toString().trim
        }
    }

    private def saveSliceImmediately(slice: String, ioFileProcessor: IOFileProcessor, packageName: String): Unit = {
        val fileName = s"$outputDirectory/${packageName}_slice_${sliceCount}.txt"
        sliceCount += 1
        ioFileProcessor.saveOutputPackage(fileName, slice)
        if (DEBUG_MODE) {
            println(s"Sliced path saved to: $fileName")
        }
    }

    /**
     * Use static program slicing technique to handle empty slice cases
     *
     * @param sources
     * @param sinks
     * @param ioFileProcessor
     * @param packageName
     */
    private def handleThinSlices(
        sources: List[Expression],
        sinks: List[Expression],
        ioFileProcessor: IOFileProcessor,
        packageName: String
    ): Unit = {

        val seenHashes = mutable.Set[String]()
        val sourceIds = sources.map(_.id).toSet

        sinks.foreach { sink =>

            val rawSlice = backwardThinSlice(sink, sourceIds)

            if (rawSlice.nonEmpty) {

                val prunedSlice = pruneSlice(rawSlice)

                val hash = semanticHash(prunedSlice)

                if (!seenHashes.contains(hash)) {
                    seenHashes += hash
                    val text = formatSlice(prunedSlice)
                    saveSliceImmediately(text, ioFileProcessor, packageName)
                }
            }
        }
    }

    private def backwardThinSlice(
        sink: Expression,
        sourceIds: Set[Long]
    ): Set[Expression] = {

        val visited = mutable.Set[Long]()
        val worklist = mutable.Stack[(Expression, Int)]()
        val slice = mutable.Set[Expression]()

        worklist.push((sink, 0))

        while (worklist.nonEmpty) {

            val (current, depth) = worklist.pop()

            if (!visited.contains(current.id) && depth <= MAX_DEPTH) {

                visited += current.id
                slice += current

                if (!sourceIds.contains(current.id)) {

                    val predecessors =
                        getRelevantDefs(current)

                    predecessors.foreach { p =>
                        worklist.push((p, depth + 1))
                    }
                }
            }
        }

        if (slice.exists(n => sourceIds.contains(n.id)))
            slice.toSet
        else
            Set.empty
    }

    private def getRelevantDefs(current: Expression): List[Expression] = {
        val defs = current._reachingDefIn.collect { case e: Expression => e }.toList

        defs.filter { d =>
            val shared = shareVariable(d, current)
            val notBoilerplate = !isNoiseNode(d)
            // Ensure we aren't just following 'this' or 'id'
            val notGlobalBridge = !d.code.contains("this.id") 
            
            shared && notBoilerplate && notGlobalBridge
        }
    }


    private def shareVariable(a: Expression, b: Expression): Boolean = {
        val varsA = extractVars(a)
        val varsB = extractVars(b)
        varsA.intersect(varsB).nonEmpty
    }

    private def extractVars(expr: Expression): Set[String] = {
        // Use the AST to find Identifier nodes instead of string splitting
        val identifiers = expr.astMinusRoot.isIdentifier.name.toSet
        val memberAccess = expr.astMinusRoot.isCall.nameExact("<operator>.fieldAccess").code.toSet
        
        // Filter out "this" to prevent class-wide pollution
        (identifiers ++ memberAccess).filterNot(_ == "this")
    }

    private def extractDefinedVar(expr: Expression): String = {
        val parts = expr.code.split("=")
        if (parts.length > 1) parts(0).trim else expr.code
    }


    private val TOP_K = 50

    private def pruneSlice(slice: Set[Expression]): Set[Expression] = {

        val filtered =
            slice.filter(isRelevantNode)

        val scored =
            filtered.toList.sortBy(e => -score(e))

        scored.take(TOP_K).toSet
    }


    private def isNoiseNode(node: Expression): Boolean = {
        val code = node.code.toLowerCase
        code.contains("_defineproperty") || 
        code.contains("logger.") || 
        code.contains("console.log") ||
        code.contains("void 0") ||
        code.contains("=== null")
    }

    private def isRelevantNode(node: Expression): Boolean = {
        if (isNoiseNode(node)) return false
        
        val code = node.code
        // Prioritize actual logic over generic assignments
        code.contains("getParameter") || 
        code.contains("execute") || 
        code.contains("query") ||
        (code.contains("=") && !code.contains("this.id =")) // Ignore the ID assignment bridge
    }



    private def score(node: Expression): Int = {

        val code = node.code
        var s = 0

        if (code.contains("getParameter")) s += 10
        if (code.contains("execute")) s += 10
        if (code.contains("query")) s += 8

        if (code.contains("=")) s += 3

        if (code.length > 120) s -= 3
        if (isPassThrough(node)) s -= 5

        s
    }


    private def isPassThrough(node: Expression): Boolean = {
        node.code.matches("""\w+\s*=\s*\w+""")
    }


    private def semanticHash(slice: Set[Expression]): String = {
        slice.map(_.code.trim).toList.sorted.mkString("\n")
    }

    private def formatSlice(nodes: Set[Expression]): String = {

        val sorted =
            nodes.toList
              .filter(n =>
                  n.lineNumber.isDefined &&
                    Option(n.code).exists(_.trim.nonEmpty)
              )
              .sortBy(_.lineNumber.get)

        val cleanedLines =
            sorted
              .flatMap { n =>
                  Option(n.code)
                    .getOrElse("")
                    .split("\n")                    // break multiline nodes
                    .map(_.trim)
              }
              .map(_.replaceAll("(?s)/\\*.*?\\*/", ""))  // remove block comments
              .map(_.replaceAll("(?m)//.*$", ""))        // remove inline comments
              .map(_.trim)
              .filter(_.nonEmpty)
              .filterNot(_.startsWith("_tmp_"))          // remove synthetic tmp vars
              .filterNot(_.startsWith("<lambda>"))       // remove lambda labels
              .filterNot(_.startsWith("\""))             // remove raw string nodes
              .filterNot(_.startsWith("'"))              // remove raw string nodes
              .distinct

        cleanedLines.mkString("\n")
    }

    /**
     * Main Function use to analyse code.
     * Each slice is saved to disk immediately after being generated
     * to avoid memory accumulation.
     */
    def runDataFlowAnalysis(ioFileProcessor: IOFileProcessor, packageName: String): Unit = {

        val sourcesByGroup = collectSources()
        val sinksByGroup   = collectSinks()
        for {
            (sourceGroup, sources) <- sourcesByGroup if sources.nonEmpty
            (sinkGroup, sinks)     <- sinksByGroup if sinks.nonEmpty
        } {
            if(!sources.equals(sinks)){
                val flows = sinks.reachableByFlows(sources).l
                println(s"\n=== Analyzing flows from $sourceGroup to $sinkGroup ===")
                println("Source: " + sourceGroup.getClass.getSimpleName + " - " + sources.size + "-" + sources.lineNumber.mkString(","))
                print("Sink: " + sinkGroup.getClass.getSimpleName + " - " + sinks.size)
                if (flows.nonEmpty) {

                    if (DEBUG_MODE) {
                        println(s"\n=== [ALERT] FOUND FLOWS FROM $sourceGroup TO $sinkGroup ===")
                        println(s"Total flows: ${flows.size}")
                    }

                    val slice = buildFlowSlice(flows)
                    saveSliceImmediately(slice, ioFileProcessor, packageName)

                } else {
                    handleThinSlices(sources, sinks, ioFileProcessor, packageName)
                }
            }
        }
    }

    def close(): Unit = {
        cpg.close()
    }
}