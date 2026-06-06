package Type.Source

import Type.{CodeType, CallType, RegexType, TypeDefinition}

object SourceGroups {

  /**
   * NHÓM 1: CÁC HÀM XỬ LÝ FILE (FILE OPERATIONS)
   */
  private val FILE_OPERATIONS: Seq[TypeDefinition] = Seq(
    RegexType("readFile"),
    RegexType("readFileSync"),
    RegexType("read"),
    RegexType("readSync"),
    RegexType("readv"),
    RegexType("readvSync"),
    RegexType("createReadStream"),
    RegexType("open"),
    RegexType("openSync"),
    RegexType("openAsBlob"),
    RegexType("opendir"),
    RegexType("opendirSync"),
    RegexType("readdir"),
    RegexType("readdirSync"),
    RegexType("readlink"),
    RegexType("readlinkSync"),
    RegexType("realpath"),
    RegexType("realpathSync"),

    RegexType("Dir"),
    RegexType("Dirent"),
    RegexType("ReadStream"),
    RegexType("FileReadStream"),

    RegexType("access"),
    RegexType("accessSync"),
    RegexType("exists"),
    RegexType("existsSync"),
    RegexType("stat"),
    RegexType("statSync"),
    RegexType("fstat"),
    RegexType("fstatSync"),
    RegexType("lstat"),
    RegexType("lstatSync"),
    RegexType("statfs"),
    RegexType("statfsSync"),
    RegexType("Stats"),
    RegexType("_toUnixTimestamp")
  )

  /**
   * NHÓM 2: THU THẬP THÔNG TIN (INFORMATION GATHERING)
   */
  private val INFORMATION_GATHERING: Seq[TypeDefinition] = Seq(
    // ----- OS Module (Methods) -----
    RegexType("userInfo"),       // userInfo
    RegexType("networkInterfaces"),
    RegexType("cpus"),
    RegexType("homedir"),
    RegexType("platform"),
    RegexType("hostname"),
    RegexType("arch"),
    RegexType("release"),
    RegexType("type"),
    RegexType("version"),        // version, proceversion
    RegexType("machine"),
    RegexType("tmpdir"),
    RegexType("totalmem"),
    RegexType("freemem"),
    RegexType("loadavg"),
    RegexType("uptime"),         // uptime, proceuptime
    RegexType("endianness"),
    RegexType("getPriority"),
    RegexType("availableParallelism"),

    // ----- Process Module (Methods) -----
    RegexType("procecwd"),            // procecwd()
    RegexType("procegetuid"),
    RegexType("procegeteuid"),
    RegexType("procegetgid"),
    RegexType("procegetegid"),
    RegexType("procegetgroups"),
    RegexType("procecpuUsage"),
    RegexType("procememoryUsage"),
    RegexType("proceresourceUsage"),
    RegexType("proceconstrainedMemory"),
    RegexType("procehrtime"),
    RegexType("proceopenStdin"),
    RegexType("procegetActiveResourcesInfo"),
    RegexType("proce_getActiveRequests"),
    RegexType("proce_getActiveHandles"),

    // ----- Process Properties (Variables/Fields) -----
    CodeType("proceenv"),
    CodeType("proceargv"),
    CodeType("proceversion"),
    CodeType("procepid"),
    CodeType("proceplatform"),
    CodeType("procearch"),

    // ----- DNS Module (Methods) -----
    RegexType("lookup"),
    RegexType("lookupService"),
    RegexType("resolve"),
    RegexType("resolve4"),
    RegexType("resolve6"),
    RegexType("resolveAny"),
    RegexType("resolveCaa"),
    RegexType("resolveCname"),
    RegexType("resolveMx"),
    RegexType("resolveNaptr"),
    RegexType("resolveNs"),
    RegexType("resolvePtr"),
    RegexType("resolveSoa"),
    RegexType("resolveSrv"),
    RegexType("resolveTxt"),
    RegexType("reverse"),
    RegexType("getServers"),
    RegexType("Resolver")
  )

  /**
   * NHÓM 3: GIAO TIẾP MẠNG (NETWORK COMMUNICATION)
   */
  private val NETWORK_COMMUNICATION: Seq[TypeDefinition] = Seq(
    // HTTP/HTTPS/Net Methods
    RegexType("createServer"),         // hcreateServer, createServe
    RegexType("createServer"),
    RegexType("createServer"),
    RegexType("createSecureServer"),   // http
    RegexType("createConnection"),     // createConnection
    RegexType("connect"),              // connect, htconnect
    RegexType("createSecurePair"),
    RegexType("createSocket"),         // dgram.createSocket
    
    // Client Requests
    RegexType("get"),                  // http.get, https.get
    RegexType("request"),              // http.request

    // Classes / Constructors
    RegexType("Server"),
    RegexType("IncomingMessage"),
    RegexType("ServerResponse"),
    RegexType("Socket"),
    RegexType("Stream"),
    RegexType("TLSSocket"),
    RegexType("WebSocket"),
    
    // Internal / Specific
    RegexType("_connectionListener"),
    RegexType("_createServerHandle"),
    RegexType("_setSimultaneousAccepts"),            
    RegexType("Http2ServerRequest"),
    RegexType("Http2ServerResponse")
  )

  def getAllSources: Set[TypeDefinition] = {
    (FILE_OPERATIONS ++
      INFORMATION_GATHERING ++
      NETWORK_COMMUNICATION).toSet
  }
}