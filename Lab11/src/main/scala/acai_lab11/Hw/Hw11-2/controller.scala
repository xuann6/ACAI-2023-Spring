package acai_lab11.Hw2

import chisel3._
import chisel3.util._
import chisel3.util.random._
import acai_lab11.AXI._

object LFSR16 {
  def apply(width: Int, increment: Bool = true.B, seed: Option[BigInt] = Some(1)): UInt =
    FibonacciLFSR.maxPeriod(width, increment, seed, XOR)
}

class controller_4_way extends Module {
  val io = IO(new Bundle {
    
    /* 
    // cpu
    /* (Hsuan) what's the matter */ val size = Input(UInt(4.W))
    val addr = Flipped(Decoupled(UInt(32.W)))
    val wdata = new wdata() // data (I), writeResp (O)
    val rdata = new rdata() // data (O)

    // cache
    val tag = Input(Vec(2,UInt(20.W)))
    val cache_wen = Output(Bool())
    val cache_iscpu = Output(Bool())
    val cache_addr = Output(UInt(32.W))
    val cache_wdata = Output(UInt(32.W))
    val cache_wsize = Output(UInt(4.W))
    val chosenWay = Output(UInt(1.W))
    val cache_rdata = Input(Vec(2,UInt(32.W)))
    val cache_dm_read = Input(UInt(256.W))
    val cache_dm_write = Output(UInt(256.W))

    // dm
    val dm_axi = new AXIMasterIF(32, 256)*/
   
    // (Hsuan) modified 
    // cpu
    val addr = Flipped(Decoupled(UInt(32.W)))
    val wen = Input(Bool())
    val ren = Input(Bool())
    val writeResp = Decoupled(UInt(1.W))
    val wdata_ready = Output(Bool())
    val rdata_valid = Output(Bool())
    val count = Output(UInt(32.W))
    val chosenWay = Output(UInt(2.W))

    // cache
    val tag = Input(Vec(4,UInt(21.W)))  // cannot be parameterized, 4 for 4-way, 21 for (valid + dirty + tag)
    val cache_wen = Output(Bool())
    val cache_iscpu = Output(Bool())

    // dm
    val dm_axi = new dm_axiif()
  
    // performance counter
    val read_hits = Output(UInt(20.W))
    val write_hits = Output(UInt(20.W))
    val read_misses = Output(UInt(20.W))
    val write_misses = Output(UInt(20.W))
    val access_counts = Output(UInt(20.W))

  })


  val cacheAccessCounter = RegInit(0.U(20.W))
  val cacheReadHitCounter = RegInit(0.U(20.W))
  val cacheWriteHitCounter = RegInit(0.U(20.W))
  val cacheReadMissCounter = RegInit(0.U(20.W))
  val cacheWriteMissCounter = RegInit(0.U(20.W))

  io.access_counts := cacheAccessCounter
  io.read_hits := cacheReadHitCounter
  io.write_hits := cacheWriteHitCounter
  io.read_misses := cacheReadMissCounter
  io.write_misses := cacheWriteMissCounter

  val sIdle :: read :: returndata :: r_dirty :: writeback_r :: dm_read :: write :: w_dirty :: writeback_w :: dm_w_read :: write_finish :: Nil = Enum(11)
  val state = RegInit(sIdle)
  val count = RegInit(0.U(32.W))

  val hit = WireDefault((io.addr.bits(31,13)===io.tag(0)(18,0) && io.tag(0)(19)===1.U)
                      ||(io.addr.bits(31,13)===io.tag(1)(18,0) && io.tag(1)(19)===1.U)
                      ||(io.addr.bits(31,13)===io.tag(2)(18,0) && io.tag(2)(19)===1.U)
                      ||(io.addr.bits(31,13)===io.tag(3)(18,0) && io.tag(3)(19)===1.U)) // valid + tag match = cache hit

  val LRUTable =
    RegInit(VecInit(Seq.fill(256)(0.U(3.W))))   // LRU look-up table
                                                // default cache height = 1024
                                                // 2-way height = 1024/2 = 512 
                                                // 4-way height = 1024/4 = 256
  
  // 4-way Pseudo-LRU replacement policy
  val chosenWay = WireDefault(Mux(hit, 
                                      // if cache hit
                                      MuxLookup(io.addr.bits(31,13), 0.U, Seq(
                                        io.tag(0)(18,0) -> 0.U,
                                        io.tag(1)(18,0) -> 1.U,
                                        io.tag(2)(18,0) -> 2.U,
                                        io.tag(3)(18,0) -> 3.U,
                                      )), 

                                      // if cache miss (need LRU policy)
                                      MuxLookup(LRUTable(io.addr.bits(12,5)), 0.U, Seq(
                                        "b00".U -> 0.U,
                                        "b01".U -> 1.U,
                                        "b10".U -> 2.U,
                                        "b11".U -> 3.U,
                                      ))
                                  )  
                              )  

  val dirty = WireDefault(io.tag(chosenWay)(20)===1.U) // first bit of tag is dirty bit
  
  io.count := count
  io.chosenWay := chosenWay

  // 給預設值
  io.rdata_valid := false.B
  io.addr.ready := false.B
  io.wdata_ready := false.B
  io.writeResp.valid := false.B
  io.writeResp.bits := 0.U
  io.cache_wen := 0.U
  io.cache_iscpu := true.B
  io.dm_axi.writeAddr_valid := false.B
  io.dm_axi.writeData_valid := false.B
  io.dm_axi.writeData_last := true.B
  io.dm_axi.readAddr_valid := false.B
  io.dm_axi.readData_ready := false.B

  // state machine，根據state圖上的條件，決定st的轉換
  switch(state) {
    is(sIdle) {
      count := 0.U
      when(io.wen) {
        state := write
      }.elsewhen(io.ren) {
        state := read
      }.otherwise {
        state := sIdle
      }
    }
    is(read) {
      cacheAccessCounter := cacheAccessCounter + 1.U
      when(hit) {
        cacheReadHitCounter := cacheReadHitCounter + 1.U
        state := returndata
      }.otherwise {
        cacheReadMissCounter := cacheReadMissCounter + 1.U
        state := r_dirty
      }
      
    }
    is(r_dirty) {
      when(dirty) {
        state := writeback_r
      }.otherwise {
        state := dm_read
      }
    }
    is(writeback_r) {
      when(io.dm_axi.writeRespvalid) {
        state := returndata
      }.otherwise {
        state := writeback_r
      }
    }
    is(dm_read) {
      when(io.dm_axi.readRespvalid) {
        state := returndata
      }.otherwise {
        state := dm_read
      }
    }
    is(returndata) {
      state := sIdle
    }
    is(write) {
      cacheAccessCounter := cacheAccessCounter + 1.U
      when(hit) {
        cacheWriteHitCounter := cacheWriteHitCounter +1.U
        state := write_finish
      }.otherwise {
        cacheWriteMissCounter := cacheWriteMissCounter + 1.U
        state := w_dirty
      }
    }
    is(w_dirty) {
      when(dirty) {
        state := writeback_w
      }.otherwise {
        state := dm_w_read
      }
    }
    is(writeback_w) {
      when(io.dm_axi.writeRespvalid) {
        state := write_finish
      }.otherwise {
        state := writeback_w
      }
    }
    is(dm_w_read) {
      when(io.dm_axi.readRespvalid) {
        state := write_finish
      }.otherwise {
        state := dm_w_read
      }
    }
    is(write_finish) {
      state := sIdle
    }
  }

//每個st的回傳訊號
  switch(state) {
    is(sIdle) {
      io.rdata_valid := false.B
      io.addr.ready := true.B
      io.wdata_ready := true.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B
    }
    is(read) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B
    }
    is(r_dirty) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B
    }
    is(writeback_r) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := io.dm_axi.readRespvalid
      io.cache_iscpu := false.B
      io.dm_axi.writeAddr_valid := true.B
      io.dm_axi.writeData_valid := true.B
      io.dm_axi.writeData_last := count === 8.U
      io.dm_axi.readAddr_valid := true.B
      io.dm_axi.readData_ready := true.B
      count := count + 1.U

    }
    is(dm_read) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := io.dm_axi.readRespvalid
      io.cache_iscpu := false.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := count === 8.U
      io.dm_axi.readAddr_valid := true.B
      io.dm_axi.readData_ready := true.B
      count := count + 1.U
    }
    is(write) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B
    }
    is(w_dirty) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B
    }
    is(writeback_w) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := io.dm_axi.readRespvalid
      io.cache_iscpu := false.B
      io.dm_axi.writeAddr_valid := true.B
      io.dm_axi.writeData_valid := true.B
      io.dm_axi.writeData_last := count === 8.U
      io.dm_axi.readAddr_valid := true.B
      io.dm_axi.readData_ready := true.B
      count := count + 1.U
    }
    is(dm_w_read) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := io.dm_axi.readRespvalid
      io.cache_iscpu := false.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := true.B
      io.dm_axi.readData_ready := true.B
      count := count + 1.U
    }
    is(write_finish) {
      io.rdata_valid := false.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.writeResp.valid := true.B
      io.writeResp.bits := 1.U
      io.cache_wen := 1.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B

      // 4-way RR replacement policy
      LRUTable(io.addr.bits(12,5)) := LFSR16(2)

    }
    is(returndata) {
      io.rdata_valid := true.B
      io.addr.ready := false.B
      io.wdata_ready := false.B
      io.cache_wen := 0.U
      io.cache_iscpu := true.B
      io.dm_axi.writeAddr_valid := false.B
      io.dm_axi.writeData_valid := false.B
      io.dm_axi.writeData_last := false.B
      io.dm_axi.readAddr_valid := false.B

      // 4-way RR replacement policy
      LRUTable(io.addr.bits(12,5)) := LFSR16(2)

    }
  }
}

