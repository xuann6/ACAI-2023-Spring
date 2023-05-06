// Create a module for vector reg arithmetic
package acai_lab11.PiplinedCPU.DatapathModule.DatapathComponent

import chisel3._
import chisel3.util._ 

object vector_ALU_op{
    val VADD_VV = 0.U
    val VMUL_VX = 1.U
    val VSL = 2.U
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
      //choise 1
      //wire_set(0) := io.vector_src1(7,0)   + io.vector_src2(7,0)     
      //wire_set(1) := io.vector_src1(15,8)  + io.vector_src2(15,8)
      //wire_set(2) := io.vector_src1(23,16) + io.vector_src2(23,16)
      //wire_set(3) := io.vector_src1(31,24) + io.vector_src2(31,24)
      //wire_set(4) := io.vector_src1(39,32) + io.vector_src2(39,32)
      //wire_set(5) := io.vector_src1(47,40) + io.vector_src2(47,40)
      //wire_set(6) := io.vector_src1(55,48) + io.vector_src2(55,48)
      //wire_set(7) := io.vector_src1(63,56) + io.vector_src2(63,56)  


      //choise 2
      val src1_unit = io.vector_src1.asTypeOf(Vec(64,UInt(8.W)))
      val src2_unit = io.vector_src2.asTypeOf(Vec(64,UInt(8.W)))
      
      wire_set.zip(src1_unit zip src2_unit).map{
        case(wire,(src1,src2))=>
          wire := src1 + src2
      }    
    }
    is(VMUL_VX){
      for(i <- 0 until 64) {
        wire_set(i) := io.vector_src2(8*i + 7, 8*i) * io.vector_src1(31,0)
      }
    }
    is(VSL){
      for(i <- 1 until 64) {
        wire_set(i) := 0.U
      }
      wire_set(0) := io.vector_src1(31,0)  
    }
  }

  //test
  //val out = io.vector_src1 + io.vector_src2
  //printf("out  = %x\n",out)

  io.vector_out := wire_set.asUInt
  //printf("vout = %x\n",io.vector_out)
}
