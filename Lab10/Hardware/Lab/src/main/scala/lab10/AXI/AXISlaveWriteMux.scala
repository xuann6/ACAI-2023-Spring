package lab10.AXI

import chisel3._
import chisel3.util._

class writeOut(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val writeResp = Flipped(Decoupled(UInt(2.W))) // response from slave to check write status
  val writeAddr = Decoupled(new AXIAddress(addrWidth)) // output address to slave for writing data
  val writeData = Decoupled(new AXIWriteData(dataWidth)) // output data to write into slave
}
class writeIns(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val writeAddr = Flipped(Decoupled(new AXIAddress(addrWidth))) // input address from writebus
  val writeData = Flipped(Decoupled(new AXIWriteData(dataWidth))) // input write data from  writebus
  val writeResp = Decoupled(UInt(2.W)) // output write response(response from slave) to writebus
}

class AXISlaveWriteMux(val nMasters: Int, val addrWidth: Int, val dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val out = new writeOut(addrWidth, dataWidth)
    val ins = Vec(nMasters, new writeIns(addrWidth, dataWidth))
  })

  val arbiter = Module(new RRArbiter(new AXIAddress(addrWidth), nMasters))

  for (i <- 0 until nMasters) {
    arbiter.io.in(i) <> io.ins(i).writeAddr
  }

  // arbiter.io.in <> io.ins
  io.out.writeAddr <> arbiter.io.out

  for (i <- 0 until nMasters) {
    io.ins(i).writeData.ready := false.B
    io.ins(i).writeResp.valid := false.B
    io.ins(i).writeResp.bits := 0.U
  }

  io.out.writeData <> io.ins(arbiter.io.chosen.asUInt).writeData
  io.ins(arbiter.io.chosen.asUInt).writeResp <> io.out.writeResp
}
