package lab10.MemIF

import chisel3._
import chisel3.util._
import lab10.PiplinedCPU.wide._
import lab10.AXI._


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
  // Wlast := true.B // 1 transfer

	io.bus_master.readAddr.bits.addr := io.memIF.raddr

	// io.bus_master.readAddr.bits.len := 0.U // 1 transfer (2 transfers if Vector Load/Store)
	/* Hsuan */ io.bus_master.readAddr.bits.len := Mux(io.memIF.Length>4.U, 16.U, 0.U)
  /* Hsuan */ // 16 for 512 = 32 * 16
  io.bus_master.readAddr.bits.size := "b010".U // 4bytes
	io.bus_master.readAddr.bits.burst := "b01".U // INCR

	// io.memIF.rdata := Cat(0.U((memDataWidth-busDataWidth).W),io.bus_master.readData.bits.data) // lower 32 bits are values
  

  io.bus_master.writeAddr.bits.addr := io.memIF.waddr

	// io.bus_master.writeAddr.bits.len := 0.U // 1 transfer (2 transfers if Vector Load/Store)
	/* Hsuan */ io.bus_master.writeAddr.bits.len := Mux(io.memIF.Length>4.U, 16.U, 0.U)
  io.bus_master.writeAddr.bits.size := "b010".U // 4bytes
	io.bus_master.writeAddr.bits.burst := "b01".U // INCR

	// io.bus_master.writeData.bits.data := io.memIF.wdata(busDataWidth-1,0) // lower 32 bits are values
	io.bus_master.writeData.bits.strb := MuxLookup(io.memIF.Length, 0.U, Seq(
            "b0000".U(4.W) -> "b1".U,
            "b0001".U(4.W) -> "b11".U,
            "b0010".U(4.W) -> "b1111".U,
/* Hsuan */ "b0110".U(4.W) -> "b1111".U // for vector load/store
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
        /* Hsuan */ MemAccessState := Mux(io.bus_master.writeAddr.ready, sAXIWriteSendData, sAXIWriteSendAddr)
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
      io.bus_master.readAddr.valid := io.memIF.Mem_R
      io.bus_master.writeAddr.valid := (io.memIF.Mem_W & io.bus_master.writeAddr.ready)
      io.bus_master.writeData.valid := (io.memIF.Mem_W & io.bus_master.writeData.ready)
    }
    is(sAXIReadSend) {
      io.bus_master.readAddr.valid := Mux(io.bus_master.readAddr.ready, true.B, false.B)
    }
    is(sAXIReadWait) {
      io.bus_master.readData.ready := true.B
    }
    is(sAXIWriteSendAddr) {
      io.bus_master.writeAddr.valid := (io.memIF.Mem_W & io.bus_master.writeAddr.ready)
    }
    is(sAXIWriteSendData) {
      io.bus_master.writeData.valid := (io.memIF.Mem_W & io.bus_master.writeData.ready)
    }
    is(sAXIWriteWait) {
      io.bus_master.writeResp.ready := true.B
      io.bus_master.writeData.valid := false.B
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

  // (Hsuan)
  val rdata_Reg = RegInit(0.U(512.W))
  val wdata_Reg = RegInit(0.U(512.W))
  val wdata_Cnt = RegInit(0.U(4.W))

  val burst_Flag = RegInit(0.U(1.W))

  switch(MemAccessState) {
    is(sNormal) {
      rdata_Reg := 0.U(512.W)
      when (io.memIF.Mem_W) { wdata_Reg := io.memIF.wdata }
      wdata_Cnt := Mux(io.memIF.Length>4.U, io.bus_master.writeAddr.bits.len-1.U, 0.U)
      burst_Flag := Mux(io.memIF.Length>4.U, 1.U, 0.U)
    }
    is(sAXIReadSend) {
    }
    is(sAXIReadWait) {
      rdata_Reg := Mux(io.bus_master.readData.valid && burst_Flag===1.U, (rdata_Reg >> 32) + Cat(io.bus_master.readData.bits.data, 0.U(480.W)), 
                   Mux(io.bus_master.readData.valid && burst_Flag===0.U, Cat(0.U(480.W), io.bus_master.readData.bits.data), rdata_Reg))
    }
    is(sAXIWriteSendAddr) {
    }
    is(sAXIWriteSendData) {
      wdata_Cnt := Mux(io.bus_master.writeData.valid & Wlast, wdata_Cnt, 
                   Mux(io.bus_master.writeData.valid, wdata_Cnt - 1.U, wdata_Cnt))
      wdata_Reg := Mux(io.bus_master.writeData.valid && burst_Flag===1.U, wdata_Reg >> 32, wdata_Reg)
    }
    is(sAXIWriteWait) {
    }
  }

  /* Hsuan */ io.memIF.rdata := Mux(burst_Flag===1.U, (rdata_Reg >> 32) + Cat(io.bus_master.readData.bits.data, 0.U(480.W)), 
                                MuxLookup(io.memIF.wide, io.bus_master.readData.bits.data, Seq(
                                    Byte  -> Cat(Fill(24,io.bus_master.readData.bits.data(7)), io.bus_master.readData.bits.data(7,0)),
                                    Half  -> Cat(Fill(16,io.bus_master.readData.bits.data(15)), io.bus_master.readData.bits.data(15,0)),
                                    Word  -> io.bus_master.readData.bits.data(31,0).asUInt,
                                    UByte -> io.bus_master.readData.bits.data(7,0).asUInt,
                                    UHalf -> io.bus_master.readData.bits.data(15,0).asUInt,   
                                ))
                                )
  /* Hsuan */ io.bus_master.writeData.bits.data := Mux(burst_Flag===1.U, wdata_Reg(31,0), wdata_Reg(31,0)) 
  /* Hsuan */ Wlast := wdata_Cnt===0.U & (MemAccessState===sAXIWriteSendData || MemAccessState===sAXIWriteWait)
  
}
