package org.springframework.study;

import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class DefaultListableBeanFactoryTest {

	@Test
	public void  testBeanFactory() throws Exception{
		/**
		    BeanFactory：可以理解为IoC容器的抽象，提供了ioc容器的最基本API。
		 	ApplicationContext：IoC容器的高级形态，在基础IoC容器上加了许多特性。
		 	DefaultListableBeanFactory是IoC容器的最基础实现，是一个最基础最简单的IoC容器对象，
		 	其高级容器ApplicationContext也是通过持有DefaultListableBeanFactory引用，在基础IoC容器之上进行特性增强
		 **/

		//<1>
//		ApplicationContext context = new ClassPathXmlApplicationContext("bean.xml");
//		//<2>
//		HelloWorld helloWorld1 = (HelloWorld) context.getBean("helloWorld");
//		helloWorld1.printHelloWord();

		// <1>
		ClassPathResource resource = new ClassPathResource("bean.xml");
		// <2>
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
		// <3>
		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
		// <4>
		reader.loadBeanDefinitions(resource);
		// <5>
		HelloWorld helloWorld2 = (HelloWorld)factory.getBean("helloWorld");
		helloWorld2.printHelloWord();
	}

}
