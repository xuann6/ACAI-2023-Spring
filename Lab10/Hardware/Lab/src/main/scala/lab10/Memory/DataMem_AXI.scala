package lab10.Memory

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFile

import lab10.AXI._

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

  val sIdle :: sReadResp :: sWriteResp :: sWriteData :: Nil = Enum(4)

  val stateReg = RegInit(sIdle)
  val memory = Mem((1 << (size)), UInt(8.W))
  val Rlast = Wire(Bool())
  
  // Rlast := true.B // 1 transfer (Modify it when more than one transfers in a burst)
  io.bus_slave.readData.bits.last := Rlast

  loadMemoryFromFile(memory, binaryFile)

  // Next state decoder
  switch(stateReg) {
    is(sIdle) {
      when(io.bus_slave.readAddr.valid) {
        stateReg := sReadResp
      }.elsewhen(io.bus_slave.writeAddr.valid) {
        stateReg := sWriteData
      }.otherwise {
        stateReg := sIdle
      }
    }
    is(sReadResp) {
      stateReg := Mux((io.bus_slave.readData.ready & Rlast), sIdle, sReadResp)
    }
    is(sWriteResp) {
      stateReg := Mux((io.bus_slave.writeResp.ready), sIdle, sWriteResp)
    }
    is(sWriteData) {
      stateReg := Mux((io.bus_slave.writeData.valid & io.bus_slave.writeData.bits.last), sWriteResp, sWriteData)
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
    }
    is(sReadResp) {
      io.bus_slave.readData.valid := true.B
    }
    is(sWriteResp) {
      io.bus_slave.writeResp.valid := true.B
    }
    is(sWriteData) {
      io.bus_slave.writeData.ready := true.B
    }
  }

  // Handle request
  val addrReg = RegInit(0.U(addrWidth.W))

  /// Test
  io.is_Write := (stateReg===sWriteData)
  io.is_Read := (stateReg===sReadResp)
  io.addr := addrReg
  io.wdata := io.bus_slave.writeData.bits.data
  io.rdata := io.bus_slave.readData.bits.data

  switch(stateReg) {
    is(sIdle) {
      addrReg := Mux(io.bus_slave.readAddr.valid, io.bus_slave.readAddr.bits.addr - "h8000".U,
        Mux(io.bus_slave.writeAddr.valid,(io.bus_slave.writeAddr.bits.addr - "h8000".U), addrReg))
    }
    is(sReadResp) {
      addrReg := Mux(io.bus_slave.readData.ready & ~ Rlast, addrReg + 4.U(addrWidth.W), addrReg)
    }
    is(sWriteResp) {
      addrReg := addrReg
    }
    is(sWriteData) {
      when(io.bus_slave.writeData.valid) {
        for (i <- 0 until (dataWidth / 8)) {
          memory(addrReg + i.U) := Mux(
            (io.bus_slave.writeData.bits.strb(i) === 1.U),
            io.bus_slave.writeData.bits.data(8 * (i + 1) - 1, 8 * i),
            memory(addrReg + i.U)
          )
        }
      }
      addrReg := Mux(io.bus_slave.writeData.valid  & ~ io.bus_slave.writeData.bits.last, addrReg + 4.U(addrWidth.W), addrReg)
    }
  }

  io.bus_slave.readData.bits.data := Cat(
    memory(addrReg + 3.U), memory(addrReg + 2.U), memory(addrReg + 1.U), memory(addrReg)
  )
  io.bus_slave.readData.bits.resp := 0.U
  io.bus_slave.writeResp.bits := 0.U


  // (Hsuan)
  val lenReg = RegInit(0.U(8.W))

  switch(stateReg) {
    is(sIdle) {
      lenReg := Mux(io.bus_slave.readAddr.bits.len>4.U, io.bus_slave.readAddr.bits.len-1.U, 0.U)
    }
    is(sReadResp) {
      lenReg := lenReg - 1.U
    }
    is(sWriteResp) {
    }
    is(sWriteData) {
    }
  }

  Rlast := lenReg===0.U & stateReg===sReadResp

}
