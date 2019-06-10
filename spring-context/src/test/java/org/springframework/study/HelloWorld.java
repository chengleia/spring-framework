package org.springframework.study;

import org.springframework.beans.factory.annotation.Autowired;

public class HelloWorld {

	public HelloWorld(){

	}

	public HelloWorld(int i){
		System.out.println(i);
	}

	@Autowired
	public HelloWordTwo helloWordTwo;

	public void printHelloWord(){
		System.out.println("我是一只HelloWord");
	}

	public void setHelloWordTwo(HelloWordTwo helloWordTwo) {
		this.helloWordTwo = helloWordTwo;
	}

}
