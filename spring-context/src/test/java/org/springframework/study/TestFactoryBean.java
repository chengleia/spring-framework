package org.springframework.study;

import org.springframework.beans.factory.FactoryBean;

public class TestFactoryBean implements FactoryBean {

	@Override
	public Object getObject() throws Exception {
		return new HelloWordTwo();
	}

	@Override
	public Class<?> getObjectType() {
		return HelloWordTwo.class;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}
}
