package org.springframework.study;

public class A {
	private B b;

	private String str = "test string";

	private int number = 1;

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
