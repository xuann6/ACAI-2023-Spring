// Create a module for vector reg arithmetic
package lab10.PiplinedCPU.DatapathModule.DatapathComponent

import chisel3._
import chisel3.util._ 

object vector_ALU_op{
    val VADD_VV = 0.U
    val VMUL_VX = 4.U
}

import vector_ALU_op._

class VECTOR_ALUIO extends Bundle{
  val vector_src1    = Input(UInt(512.W))
  val vector_src2    = Input(UInt(512.W))
  val vector_ALUSel  = Input(UInt(4.W))
  val vector_out     = Output(UInt(512.W))
}

class Vector_ALU extends Module{
  val io = IO(new VECTOR_ALUIO) 

  val wire_set = Wire(Vec(64,UInt(8.W)))

  //Default Value of wire_set
  wire_set.foreach{wire=>
    wire := 0.U
  }

  switch(io.vector_ALUSel){
    // Improved version
    is(VADD_VV){  
      val src1_unit = io.vector_src1.asTypeOf(Vec(64,UInt(8.W)))
      val src2_unit = io.vector_src2.asTypeOf(Vec(64,UInt(8.W)))
      wire_set.zip(src1_unit zip src2_unit).map{
        case(wire,(src1,src2))=>
          wire := src1 + src2
      }    
    }
    is(VMUL_VX){
      val src1_unit = io.vector_src1.asTypeOf(Vec(64,UInt(8.W)))
      val src2_unit = Fill(64,io.vector_src2(7,0).asUInt).asTypeOf(Vec(64,UInt(8.W)))
      // val src2_unit = (io.vector_src2(7,0).asUInt).asTypeOf(Vec(64,UInt(8.W)))
      // wire_set := src2_unit
      wire_set.zip(src1_unit zip src2_unit).map{
        case(wire,(src1,src2))=>
          wire := src1 * src2
      }    
    }
  }

  //test
  //val out = io.vector_src1 + io.vector_src2
  //printf("out  = %x\n",out)

  io.vector_out := wire_set.asUInt
  //printf("vout = %x\n",io.vector_out)
}
