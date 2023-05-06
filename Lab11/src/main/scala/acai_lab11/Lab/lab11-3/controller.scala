package acai_lab11.Lab3

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

    // cache
    val tag = Input(UInt(19.W))
    val cache_wen = Output(Bool())
    val cache_iscpu = Output(Bool())

    // dm
    val dm_axi = new dm_axiif()

  })

  val sIdle :: read :: returndata :: r_dirty :: writeback_r :: dm_read :: write :: w_dirty :: writeback_w :: dm_w_read  :: write_finish :: Nil =
    Enum(11)
  val state = RegInit(sIdle)
  val count = RegInit(0.U(32.W))
  val dirty = WireDefault(io.tag(18)) // tag的第一位是 dirty bit
  val hit = WireDefault(io.tag(16,0) === io.addr.bits(31,15)&& io.tag(17) === 1.U) //tag是addr的31~15位，tag的第二位是 valid bit
  val cache_raddr = WireDefault(Cat(io.tag(16,0), io.addr.bits(14,5),0.U(5.W))) //tag是addr的31~15位，所以加上addr的14~5位(index部分)，offset的部分補0

  io.count := count

  // default value - controller <-> CPU
  io.rdata_valid := false.B
  io.addr.ready := false.B
  io.wdata_ready := false.B
  io.writeResp.valid := false.B
  io.writeResp.bits := 0.U

  // controller <-> cacheMem
  io.cache_wen := 0.U
  io.cache_iscpu := true.B

  // controller <-> AXI bus
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
      when(hit) {
        state := returndata
      }.otherwise {
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
      when(io.dm_axi.writeRespvalid) { // before jump into next status, make sure sucessfully writeback 
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
      when(hit) {
        state := write_finish
      }.otherwise {
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

  // control signal
  // cache     : contoller  <-> cache
  // dm_axi    : controller <-> AXI
  // otherwise : controller <-> cpu
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
      count := count + 1.U // new one

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
    }
  }

}

