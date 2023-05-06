package acai_lab11.IF

import chisel3._
import chisel3.util._

import acai_lab11.AXI._

/* Direct Memory InterFace */
class AXI_IF(memAddrWidth: Int, memDataWidth: Int, busDataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val memIF = new MemIF_MEM(memAddrWidth, memDataWidth)
    // AXI
    val bus_master = new AXIMasterIF(memAddrWidth, busDataWidth)
  })

  // AXI
  val Mem_raddr = RegInit(0.U(memAddrWidth.W))
  val Mem_waddr = RegInit(0.U(memAddrWidth.W))
  val Wlast = Wire(Bool())
  val w_data_num_reg = RegInit(0.U(5.W))
  val r_data_num_reg = RegInit(0.U(5.W))
  val wire_set = Reg(Vec(64,UInt(32.W)))
  val v_wire_set = Reg(Vec(16,UInt(32.W)))


  Wlast := w_data_num_reg === io.memIF.Length  // 1 transfer

	io.bus_master.readAddr.bits.addr := io.memIF.raddr

	io.bus_master.readAddr.bits.len := io.memIF.Length // 1 transfer (2 transfers if Vector Load/Store)
	io.bus_master.readAddr.bits.size := "b010".U // 4bytes
	io.bus_master.readAddr.bits.burst := "b01".U // INCR


	io.memIF.rdata := wire_set.asUInt // lower 32 bits are values

  io.bus_master.writeAddr.bits.addr := io.memIF.waddr

	io.bus_master.writeAddr.bits.len := io.memIF.Length // 1 transfer (2 transfers if Vector Load/Store)
	io.bus_master.writeAddr.bits.size := "b010".U // 4bytes
	io.bus_master.writeAddr.bits.burst := "b01".U // INCR

	io.bus_master.writeData.bits.data := io.memIF.wdata(busDataWidth-1,0) // lower 32 bits are values
	io.bus_master.writeData.bits.strb := MuxLookup(io.memIF.Wide, 0.U, Seq(
        "b0000".U(4.W) -> "b1".U,
        "b0001".U(4.W) -> "b11".U,
        "b0010".U(4.W) -> "b1111".U,
        "b1000".U(4.W) -> "b1111".U
      ))
      
	io.bus_master.writeData.bits.last := Wlast // transfer are always 1 (2 transfers if Vector Load/Store)
  
  val sNormal :: sAXIReadSend :: sAXIReadWait :: sAXIWriteSendAddr :: sAXIWriteSendData :: sAXIWriteWait :: Nil = Enum(6)

  val MemAccessState = RegInit(sNormal)

  // MemAccessState - next state decoder
  switch(MemAccessState) {
    is(sNormal) {
      when(io.memIF.Mem_R) {
        MemAccessState := Mux(io.bus_master.readAddr.ready, sAXIReadWait, sAXIReadSend)
      }.elsewhen(io.memIF.Mem_W) {
        MemAccessState := Mux(io.bus_master.writeAddr.ready, sAXIWriteWait, sAXIWriteSendAddr)
      }.otherwise {
        MemAccessState := sNormal
      }
    }
    is(sAXIReadSend) {
      MemAccessState := Mux(io.bus_master.readAddr.ready, sAXIReadWait, sAXIReadSend)
    }
    is(sAXIReadWait) {
      MemAccessState := Mux(io.bus_master.readData.valid & io.bus_master.readData.bits.last, sNormal, sAXIReadWait)
    }
    is(sAXIWriteSendAddr) {
      MemAccessState := Mux(io.bus_master.writeAddr.ready, sAXIWriteSendData, sAXIWriteSendAddr)
    }
    is(sAXIWriteSendData) {
      MemAccessState := Mux(io.bus_master.writeData.ready & Wlast, sAXIWriteWait, sAXIWriteSendData)
    }
    is(sAXIWriteWait) {
      MemAccessState := Mux(io.bus_master.writeResp.valid, sNormal, sAXIWriteWait)
    }
  }

  // AXI output gnenrator
  io.bus_master.readAddr.valid := false.B
  io.bus_master.readData.ready := false.B
  io.bus_master.writeAddr.valid := false.B
  io.bus_master.writeData.valid := false.B
  io.bus_master.writeResp.ready := false.B

  io.memIF.Valid := io.bus_master.writeResp.valid || (io.bus_master.readData.valid & io.bus_master.readData.bits.last)

  switch(MemAccessState) {
    is(sNormal) {
      w_data_num_reg := 0.U
      r_data_num_reg := 0.U
      //Default Value of wire_set
      wire_set.foreach{wire=>
        wire := 0.U
      }
      for(i <- 0 until 16) {
        v_wire_set(i) := io.memIF.wdata(32*i+31,32*i)
      }
      
      io.bus_master.readAddr.valid := io.memIF.Mem_R
      io.bus_master.writeAddr.valid := (io.memIF.Mem_W & io.bus_master.writeAddr.ready)
    }
    is(sAXIReadSend) {
      io.bus_master.readAddr.valid := Mux(io.bus_master.readAddr.ready, true.B, false.B)
    }
    is(sAXIReadWait) {
      io.bus_master.readData.ready := true.B
      when(io.bus_master.readData.valid === true.B && !io.bus_master.readData.bits.last) {
        wire_set(r_data_num_reg) := MuxLookup(io.memIF.Wide, 0.U, Seq(
          "b0000".U(4.W) -> Cat(Fill(24,io.bus_master.readData.bits.data(7)),io.bus_master.readData.bits.data(7,0)),
          "b0001".U(4.W) -> Cat(Fill(16,io.bus_master.readData.bits.data(15)),io.bus_master.readData.bits.data(15,0)),
          "b0010".U(4.W) -> io.bus_master.readData.bits.data,
          "b0100".U(4.W) -> Cat(0.U(24.W),io.bus_master.readData.bits.data(7,0)),
          "b0101".U(4.W) -> Cat(0.U(16.W),io.bus_master.readData.bits.data(15,0)),
          "b1000".U(4.W) -> io.bus_master.readData.bits.data
        )) 
        r_data_num_reg := r_data_num_reg + 1.U
      }

    }
    is(sAXIWriteSendAddr) {
      io.bus_master.writeAddr.valid := (io.memIF.Mem_W & io.bus_master.writeAddr.ready)
    }
    is(sAXIWriteSendData) {

    }
    is(sAXIWriteWait) {

      io.bus_master.writeData.valid := (io.memIF.Mem_W & io.bus_master.writeData.ready )
      when(io.memIF.Mem_W & io.bus_master.writeData.ready & (!Wlast)){
        io.bus_master.writeData.bits.data := v_wire_set(w_data_num_reg)
        w_data_num_reg := w_data_num_reg + 1.U 
      }

      io.bus_master.writeResp.ready := true.B
    }
  }

  // Address
  switch(MemAccessState) {
    is(sNormal) {
      Mem_raddr := io.memIF.raddr
      Mem_waddr := io.memIF.waddr
    }
    is(sAXIReadSend) {
      Mem_raddr := Mem_raddr
      Mem_waddr := Mem_waddr
    }
    is(sAXIReadWait) {
      Mem_raddr := Mux(io.bus_master.readData.valid, Mem_raddr+4.U(memAddrWidth.W) ,Mem_raddr)
      Mem_waddr := Mem_waddr
    }
    is(sAXIWriteSendAddr) {
      Mem_raddr := Mem_raddr
      Mem_waddr := Mem_waddr
    }
    is(sAXIWriteSendData) {
      Mem_raddr := Mem_raddr
      Mem_waddr := Mux(io.bus_master.writeData.ready, Mem_waddr+4.U(memAddrWidth.W) ,Mem_waddr)
    }
    is(sAXIWriteWait) {
      Mem_raddr := Mem_raddr
      Mem_waddr := Mem_waddr
    }
  }
}