package org.springframework.study;

import org.springframework.beans.factory.annotation.Autowired;

public class A {

	private B b;

	private HelloWorld helloWorld;

	private String str = "test string";

	private int number = 1;

	public A(B b) {
		this.b = b;
	}

	@Autowired
	public A(B b, HelloWorld helloWorld) {
		this.b = b;
		this.helloWorld = helloWorld;
	}

	public B getB() {
		return b;
	}

	public void setB(B b) {
		this.b = b;
	}

	public String getStr() {
		return str;
	}

	public void setStr(String str) {
		this.str = str;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}
}
