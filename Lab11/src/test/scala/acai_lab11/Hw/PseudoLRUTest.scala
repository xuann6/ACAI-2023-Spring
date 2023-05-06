package acai_lab11.Hw3

import scala.io.Source
import chisel3.iotesters.{PeekPokeTester,Driver}
import scala.language.implicitConversions


class PseudoLRUTest(dut:Cache) extends PeekPokeTester(dut){
    poke(dut.io.size,15)
    var count = 0
    for(i <- 1 to 100){


        poke(dut.io.addr.valid,0)
        poke(dut.io.wdata.data.valid,0)
        step(1)
        poke(dut.io.addr.bits,i<<4)
        poke(dut.io.addr.valid,1)
        poke(dut.io.wdata.data.bits,i)
        poke(dut.io.wdata.data.valid,1)
        poke(dut.io.axiIF.readData.bits.data,0)
        
        while(peek(dut.io.wdata.writeResp.valid)==0){
            step(1)
            if(peek(dut.io.axiIF.writeAddr.valid)==1){
                step(8)
                poke(dut.io.axiIF.writeResp.valid,1) 
            }
            if(peek(dut.io.axiIF.readAddr.valid)==1){
                count+=1
            }
            if(count==8){
                poke(dut.io.axiIF.readData.bits.last,1) 
                count=0
            }
        }
        poke(dut.io.axiIF.writeResp.valid,0) 
        poke(dut.io.axiIF.readData.bits.last,0) 
        poke(dut.io.addr.valid,0)
        poke(dut.io.wdata.data.valid,0)
    }
    step(1)
    var error = 0
    for(i <- 1 to 100){

        poke(dut.io.addr.bits,i<<4)
        poke(dut.io.addr.valid,1)
        poke(dut.io.wdata.data.valid,0)
        step(1)
        poke(dut.io.axiIF.readData.bits.data,0)
        println("----Read"+i.toString)
        while(peek(dut.io.rdata.data.valid)==0){
            step(1)
            if(peek(dut.io.axiIF.writeAddr.valid)==1){
                step(8)
                poke(dut.io.axiIF.writeResp.valid,1) 
            }
            if(peek(dut.io.axiIF.readAddr.valid)==1){
                count+=1
            }
            if(count==8){
                poke(dut.io.axiIF.readData.bits.last,1) 
                count=0
            }
        }
        poke(dut.io.axiIF.readData.bits.last,0) 
        if(peek(dut.io.rdata.data.bits)!=i){
            error+=1
        }
            poke(dut.io.axiIF.writeResp.valid,0)  


        println("----------- Read -----------")
        println("dataOut : "+peek(dut.io.rdata.data.bits).toString+" dataIn : "+i.toString)
        
        
        
    }
    step(3)
    if(error!=0){
        println("error : "+error.toString)
    }
    else{
        println("success")
    }

}

object PseudoLRUTest extends App{
  Driver.execute(args,()=> new Cache(1024,4,32,32)){
    dut:Cache => new PseudoLRUTest(dut)
  }
}
