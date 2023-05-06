package lab10.PiplinedCPU.DatapathModule

import chisel3._
import chisel3.util._
import lab10.PiplinedCPU.wb_sel_map._
<<<<<<< HEAD
import lab10.PiplinedCPU.vector_wb_sel_map._
=======
>>>>>>> 301bdb3 (Finished Prepare Lab10-2)

class Path_WB(addrWidth:Int) extends Module {
    val io = IO(new Bundle{

        val WB_pc_plus4_in = Input(UInt(addrWidth.W))
        val WB_alu_out_in = Input(UInt(32.W))
        val WB_ld_data_in = Input(UInt(32.W))
        val W_WBSel = Input(UInt(2.W))

        val WB_wdata = Output(UInt(32.W))
<<<<<<< HEAD

        /* lab10-4 */
        val v_WB_alu_out_in = Input(UInt(512.W))
        val v_WB_wdata = Output(UInt(512.W))

        /* lab10-4 (Hsuan) */
        val v_WBSel = Input(UInt(2.W))
        val v_ld_data = Input(UInt(512.W))
=======
>>>>>>> 301bdb3 (Finished Prepare Lab10-2)
    })
    
    // WB Wire
    io.WB_wdata := MuxLookup(io.W_WBSel, 0.U, Seq(
            PC_PLUS_4 -> io.WB_pc_plus4_in,  //from PC (+4)
<<<<<<< HEAD
            ALUOUT    -> io.WB_alu_out_in,   //from ALU
            LD_DATA   ->  io.WB_ld_data_in, //from DataMemory
        ))

    // io.v_WB_wdata := io.v_WB_alu_out_in
    io.v_WB_wdata := MuxLookup(io.v_WBSel, io.v_WB_alu_out_in, Seq(
            V_LD_DATA -> io.v_ld_data
=======
            ALUOUT -> io.WB_alu_out_in,   //from ALU
            LD_DATA ->  io.WB_ld_data_in, //from DataMemory
>>>>>>> 301bdb3 (Finished Prepare Lab10-2)
        ))

    // io.v_WB_wdata := io.v_WB_alu_out_in
    io.v_WB_wdata := MuxLookup(io.v_WBSel, io.v_WB_alu_out_in, Seq(
            V_LD_DATA -> io.v_ld_data
        ))
}
