package lab10.PiplinedCPU.DatapathModule

import chisel3._
import chisel3.util._

class Path_MEM(addrWidth:Int) extends Module {
    val io = IO(new Bundle{

        val MEM_pc_in = Input(UInt(addrWidth.W))
        val MEM_alu_out_in = Input(UInt(32.W))
        val MEM_DM_wdata_in = Input(UInt(32.W))
        
        val MEM_pc_plus_4 = Output(UInt(addrWidth.W))
        val MEM_alu_out = Output(UInt(32.W))
        val MEM_ld_data = Output(UInt(32.W))

        // Data Memory IO
        val Mem_Addr = Output(UInt(addrWidth.W))
        val Mem_Write_Data = Output(UInt(32.W))
        val Mem_Data = Input(UInt(32.W))

        /* lab 10-4*/
        val v_MEM_alu_out_in = Input(UInt(512.W))
        val v_MEM_alu_out = Output(UInt(512.W))

        /* lab 10-4 (Hsuan) */
        val v_ld_data_in = Input(UInt(512.W))
        val v_ld_data    = Output(UInt(512.W)) 
        val DM_v_wdata_in = Input(UInt(512.W))
        val DM_v_wdata = Output(UInt(512.W))
    })
    
    // pc + 4 for write back data
    io.MEM_pc_plus_4 := io.MEM_pc_in + 4.U(addrWidth.W)

    // Memory
    io.Mem_Addr := io.MEM_alu_out_in(addrWidth-1,0)
    io.Mem_Write_Data := io.MEM_DM_wdata_in
    io.MEM_ld_data := io.Mem_Data

    // other IO
    io.MEM_alu_out := io.MEM_alu_out_in
    io.v_MEM_alu_out := io.v_MEM_alu_out_in

    /* lab 10-4 (Hsuan) */
    io.v_ld_data := io.v_ld_data_in
    io.DM_v_wdata := io.DM_v_wdata_in
}
