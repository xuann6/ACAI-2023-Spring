package acai_lab11.Lab4

import chisel3._
import chisel3.util._
import acai_lab11.AXI._


class controller extends Module {
  val io = IO(new Bundle {
    // cpu
    val addr = Flipped(Decoupled(UInt(32.W)))
    val wen = Input(Bool())
    val ren = Input(Bool())
    val writeResp = Decoupled(UInt(1.W))
    val wdata_ready = Output(Bool())
    val rdata_valid = Output(Bool())
    val count = Output(UInt(32.W))
    val chosenWay = Output(UInt(1.W))

    // cache
    val tag = Input(Vec(2,UInt(20.W)))
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

  val sIdle :: read :: returndata :: r_dirty :: writeback_r :: dm_read :: write :: w_dirty :: writeback_w :: dm_w_read :: write_finish :: Nil =
    Enum(11)
  val state = RegInit(sIdle)
  val count = RegInit(0.U(32.W))
  // val cache_raddr = WireDefault(Cat(io.tag(16,0), io.addr.bits(14,5),0.U(5.W))) //tag是addr的31~15位，所以加上addr的14~5位，offset的部分補0

  io.count := count
  val hit = WireDefault((io.addr.bits(31,14)===io.tag(0)(17,0)&&io.tag(0)(18)===1.U)||(io.addr.bits(31,14)===io.tag(1)(17,0)&&io.tag(1)(18)===1.U)) //tag是addr的31~15位，tag的第二位是 valid bit

  val LRUTable =
    RegInit(VecInit(Seq.fill(512)(0.U(1.W)))) // LRU table for cache
    
  val chosenWay = WireDefault(Mux(hit,(io.addr.bits(31,14)===io.tag(1)(17,0)&&io.tag(1)(18)===1.U),!LRUTable(io.addr.bits(13,5))))
  io.chosenWay := chosenWay
  val dirty = WireDefault(io.tag(chosenWay)(19)===1.U) // tag的第一位是 dirty bit





//給預設值
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



//state machine，根據state圖上的條件，決定st的轉換
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
      LRUTable(io.addr.bits(13,5)) := chosenWay
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
      LRUTable(io.addr.bits(13,5)) := chosenWay
    }
  }

}

