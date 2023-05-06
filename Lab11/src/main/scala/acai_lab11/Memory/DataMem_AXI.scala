package acai_lab11.Memory

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

import acai_lab11.AXI._

class DataMem_AXI(size: Int, addrWidth: Int, dataWidth: Int, binaryFile: String) extends Module {
  val io = IO(new Bundle {
    val bus_slave = new AXISlaveIF(addrWidth, dataWidth)
    // Test
    val is_Write = Output(Bool())
    val is_Read = Output(Bool())
    val addr = Output(UInt(addrWidth.W))
    var wdata = Output(UInt(dataWidth.W)) 
    var rdata = Output(UInt(dataWidth.W)) 
  })

  val sIdle :: sReadResp :: sWriteResp :: sWriteData :: sRWData :: sRWResp :: Nil = Enum(6)

  val stateReg = RegInit(sIdle)
  val memory = SyncReadMem((1 << (size)), UInt(8.W))
  val Rlast = Wire(Bool())
  val Wlast = Wire(Bool())
  val counter = RegInit(0.U(addrWidth.W))
  Rlast := counter===(io.bus_slave.readAddr.bits.len)// 15.U// 1 transfer (Modify it when more than one transfers in a burst)
  io.bus_slave.readData.bits.last := Rlast
  Wlast := counter===(io.bus_slave.writeAddr.bits.len-1.U)// 15.U// 1 transfer (Modify it when more than one transfers in a burst)

  loadMemoryFromFile(memory, binaryFile)

  // Next state decoder
  switch(stateReg) {
    is(sIdle) {
      when(io.bus_slave.readAddr.valid & io.bus_slave.writeAddr.valid & io.bus_slave.writeData.valid){
        stateReg := sRWData
      }.elsewhen(io.bus_slave.readAddr.valid) {
        stateReg := sReadResp
      }.elsewhen(io.bus_slave.writeAddr.valid & io.bus_slave.writeData.valid) {
        stateReg := sWriteData
      }.otherwise {
        stateReg := sIdle
      }
    }
    is(sReadResp) {
      stateReg := Mux((io.bus_slave.readData.ready & Rlast), sIdle, sReadResp)
    }
    is(sWriteData) {
      stateReg := Mux((Wlast), sWriteResp, sWriteData)
    }
    is(sWriteResp) {
      stateReg := Mux((io.bus_slave.writeResp.ready), sIdle, sWriteResp)
    }
    is(sRWData) {
      stateReg := Mux((Wlast), sRWResp, sRWData)
    }
    is(sRWResp) {
      stateReg := Mux((io.bus_slave.writeResp.ready), sIdle, sRWResp)
    }
  }

  // AXI slave interface output - ready / valid
  io.bus_slave.readAddr.ready := false.B
  io.bus_slave.readData.valid := false.B
  io.bus_slave.writeAddr.ready := false.B
  io.bus_slave.writeData.ready := false.B
  io.bus_slave.writeResp.valid := false.B

  switch(stateReg) {
    is(sIdle) {
      io.bus_slave.readAddr.ready := true.B
      io.bus_slave.writeAddr.ready := true.B
      io.bus_slave.writeData.ready := true.B
    }
    is(sReadResp) {
      io.bus_slave.readData.valid := true.B
    }
    is(sWriteData) {
      io.bus_slave.writeData.ready := true.B
    }
    is(sWriteResp) {
      io.bus_slave.writeResp.valid := true.B
    }
    is(sRWData) {
      io.bus_slave.writeData.ready := true.B
      io.bus_slave.readData.valid := true.B
    }
    is(sRWResp) {
      io.bus_slave.writeResp.valid := true.B
      io.bus_slave.readData.valid := true.B
      io.bus_slave.readData.bits.last := true.B
    }
  }

  // Handle request
  val raddrReg = RegInit(0.U(addrWidth.W))
  val waddrReg = RegInit(0.U(addrWidth.W))
  val wdataReg = RegInit(0.U(dataWidth.W))

  /// Test
  io.is_Write := (stateReg===sWriteData)
  io.is_Read := (stateReg===sReadResp)
  io.addr := raddrReg
  io.wdata := io.bus_slave.writeData.bits.data
  io.rdata := io.bus_slave.readData.bits.data

  switch(stateReg) {
    is(sIdle) {
      raddrReg := Mux(io.bus_slave.readAddr.valid, io.bus_slave.readAddr.bits.addr, raddrReg)
      waddrReg := Mux(io.bus_slave.writeAddr.valid,(io.bus_slave.writeAddr.bits.addr), waddrReg)
      counter := 0.U
      wdataReg := io.bus_slave.writeData.bits.data
    }
    is(sReadResp) {
      raddrReg := Mux(io.bus_slave.readData.ready & ~ Rlast, raddrReg + 4.U(addrWidth.W), raddrReg)
      counter := Mux(io.bus_slave.readData.ready & ~ Rlast, counter + 1.U(addrWidth.W), counter)
    }
    is(sWriteData) {
      // when(io.bus_slave.writeData.valid) {
        for (i <- 0 until (dataWidth / 8)) {
          memory(waddrReg + i.U) := Mux(
            (io.bus_slave.writeData.bits.strb(i) === 1.U),
            wdataReg(8 * (i + 1) - 1, 8 * i),
            memory(waddrReg + i.U)
          )
        }
      // }
      wdataReg := io.bus_slave.writeData.bits.data
      waddrReg := Mux( Wlast, waddrReg, waddrReg + 4.U(addrWidth.W))
      counter := Mux( Wlast, counter, counter + 1.U(addrWidth.W))
    }
    is(sWriteResp) {
      waddrReg := waddrReg
      counter := counter
      wdataReg := io.bus_slave.writeData.bits.data
    }
    is(sRWData){
      raddrReg := Mux(io.bus_slave.readData.ready & ~ Rlast, raddrReg + 4.U(addrWidth.W), raddrReg)
      for (i <- 0 until (dataWidth / 8)) {
        memory(waddrReg + i.U) := Mux(
          (io.bus_slave.writeData.bits.strb(i) === 1.U),
          wdataReg(8 * (i + 1) - 1, 8 * i),
          memory(waddrReg + i.U)
        )
      }
      // }
      waddrReg := Mux( Wlast, waddrReg, waddrReg + 4.U(addrWidth.W))
      counter := Mux( Wlast, counter, counter + 1.U(addrWidth.W))
      wdataReg := io.bus_slave.writeData.bits.data
    }
    is(sRWResp) {
      raddrReg := Mux(io.bus_slave.readData.ready & ~ Rlast, raddrReg + 4.U(addrWidth.W), raddrReg)
      waddrReg := waddrReg
      counter := counter
      wdataReg := io.bus_slave.writeData.bits.data
    }
  }

  io.bus_slave.readData.bits.data := Cat(
    memory(raddrReg + 3.U), memory(raddrReg + 2.U), memory(raddrReg + 1.U), memory(raddrReg)
  )
  io.bus_slave.readData.bits.resp := 0.U
  io.bus_slave.writeResp.bits := 0.U
}
