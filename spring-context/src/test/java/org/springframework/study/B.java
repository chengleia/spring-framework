package org.springframework.study;

import org.springframework.beans.factory.annotation.Autowired;

import java.beans.ConstructorProperties;

public class B {


	private int i;
	private String string;

	@Autowired
	public B(int i,String s) {
		this.i = i;
		this.string = s;
	}

	@Autowired
	public B(int i) {
		this.i = i;
	}


	@Override
	public String toString() {
		return "B{" +
				"i=" + i +
				", string='" + string + '\'' +
				'}';
	}
}
