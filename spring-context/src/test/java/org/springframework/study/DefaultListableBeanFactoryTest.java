package org.springframework.study;

import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

public class DefaultListableBeanFactoryTest {

	@Test
	public void  testBeanFactory() throws Exception{
		/**
		    BeanFactory：可以理解为IoC容器的抽象，提供了ioc容器的最基本API。
		 	ApplicationContext：IoC容器的高级形态，在基础IoC容器上加了许多特性。
		 	DefaultListableBeanFactory是IoC容器的最基础实现，是一个最基础最简单的IoC容器对象，
		 	其高级容器ApplicationContext也是通过持有DefaultListableBeanFactory引用，在基础IoC容器之上进行特性增强
		 **/

//		ApplicationContext context = new ClassPathXmlApplicationContext("bean.xml");
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
//		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
//				.genericBeanDefinition(HelloWorld.class);
//		factory.registerBeanDefinition("helloWordTwo", beanDefinitionBuilder.getBeanDefinition());
//		factory.registerAlias("q","q");
//		factory.registerAlias("q","z");
		// <5>
		HelloWorld helloWorld = (HelloWorld)factory.getBean("helloWorld",new Object[]{2});
		HelloWorld helloWorld2 = (HelloWorld)factory.getBean("helloWorld",new Object[]{1});
		HelloWorld helloWorld1 = (HelloWorld)factory.getBean("helloWorld");

//		HelloWordTwo helloWorld2 = (HelloWordTwo)factory.getBean("helloWordTwo",new Object[]{1});
//		HelloWordTwo helloWorld = (HelloWordTwo)factory.getBean("helloWordTwo",new Object[]{2});
//		HelloWordTwo helloWorld1 = (HelloWordTwo)factory.getBean("helloWordTwo");

		System.out.println(helloWorld==helloWorld1);
		System.out.println(helloWorld==helloWorld2);

//		Map<String, List<String>> map = new HashMap<>();
//		List<String> list;
//
//
//		// 使用 computeIfAbsent 可以这样写
//		list = map.computeIfAbsent("list-1", k -> new ArrayList<>());
//		list.add("one");

	}

}
