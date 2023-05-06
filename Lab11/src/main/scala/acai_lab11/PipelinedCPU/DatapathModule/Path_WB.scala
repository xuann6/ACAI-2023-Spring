package acai_lab11.PiplinedCPU.DatapathModule

import chisel3._
import chisel3.util._
import acai_lab11.PiplinedCPU.wb_sel_map._

class Path_WB(addrWidth:Int) extends Module {
    val io = IO(new Bundle{

        val WB_pc_plus4_in = Input(UInt(addrWidth.W))
        val WB_alu_out_in = Input(UInt(32.W))
        val WB_ld_data_in = Input(UInt(32.W))
        val W_WBSel = Input(UInt(2.W))

        val WB_wdata = Output(UInt(32.W))

        /* acai_lab11-4 */
        val v_W_WBSel = Input(UInt(1.W))
        val v_WB_alu_out_in = Input(UInt(512.W))
        val v_WB_ld_data_in = Input(UInt(512.W))

        val v_WB_wdata = Output(UInt(512.W))

    })
    
    // WB Wire
    io.WB_wdata := MuxLookup(io.W_WBSel, 0.U, Seq(
            PC_PLUS_4 -> io.WB_pc_plus4_in,  //from PC (+4)
            ALUOUT -> io.WB_alu_out_in,   //from ALU
            LD_DATA ->  io.WB_ld_data_in, //from DataMemory
        ))
    
    // vector WB Wire
    io.v_WB_wdata := Mux(io.v_W_WBSel === 0.U, io.v_WB_alu_out_in, io.v_WB_ld_data_in)
}
