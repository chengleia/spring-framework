package org.springframework.study;

import org.springframework.beans.factory.FactoryBean;

public class TestFactoryBean implements FactoryBean {

	@Override
	public Object getObject() throws Exception {
		return new HelloWorldTwo();
	}

	@Override
	public Class<?> getObjectType() {
		return HelloWorldTwo.class;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}
}
