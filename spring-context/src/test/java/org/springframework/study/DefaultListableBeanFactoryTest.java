package org.springframework.study;

import org.junit.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ClassPathResource;

public class DefaultListableBeanFactoryTest {


	@Test
	public void  testBeanFactory() throws Exception{
		// aliasTest();
		// argsTest();
		testAutoWired();


//		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
//				.genericBeanDefinition(HelloWorldTwo.class);
//		factory.registerBeanDefinition("z", beanDefinitionBuilder.getBeanDefinition());
//		factory.registerAlias("q","q");

//		Object a1 = factory.getBean("a");
//		TestBeanPostProcessor testBeanPostProcessor = new TestBeanPostProcessor();
//		factory.addBeanPostProcessor(testBeanPostProcessor);
//		TestBeanPostProcessor test = (TestBeanPostProcessor)factory.getBean("testBeanPostProcessor");
//		test.display();

//
//		//工厂方法单例测试
//		Object obj = factory.getBean("cl");
//		Object obj1 = factory.getBean("cl");
//		System.out.println(obj == obj1);




		// factorybean的 scope和issigton方法作用
//		FactoryBean fb = (FactoryBean)factory.getBean("&fb");
//		FactoryBean fb2 = (FactoryBean)factory.getBean("&fb");
//
//		HelloWorldTwo fbb = (HelloWorldTwo)factory.getBean("fb");
//		HelloWorldTwo fbb1 = (HelloWorldTwo)factory.getBean("fb");
//
//		System.out.println(fb);
//		System.out.println(fb2);
//		System.out.println(fbb);
//		System.out.println(fbb1);
//
//		System.out.println(fb==fb2);
//		System.out.println(fbb==fbb1);

//
//		A a = (A)factory.getBean("a");
//		System.out.println(a);

//
//		System.out.println(helloWorld.i);

//		System.out.println(helloWorld.helloWorld);
//		HelloWorld helloWorld2 = (HelloWorld)factory.getBean("helloWorld",new Object[]{1});
//		HelloWorld helloWorld1 = (HelloWorld)factory.getBean("helloWorld");

//		HelloWorldTwo helloWorld2 = (HelloWorldTwo)factory.getBean("helloWorldTwo",new Object[]{1});
//		HelloWorldTwo helloWorld = (HelloWorldTwo)factory.getBean("helloWorldTwo",new Object[]{2});
//		HelloWorldTwo helloWorld1 = (HelloWorldTwo)factory.getBean("helloWorldTwo");
//
//		System.out.println(helloWorld==helloWorld1);
//		System.out.println(helloWorld==helloWorld2);

//		Map<String, List<String>> map = new HashMap<>();
//		List<String> list;
//
//
//		// 使用 computeIfAbsent 可以这样写
//		list = map.computeIfAbsent("list-1", k -> new ArrayList<>());
//		list.add("one");



	}


	static DefaultListableBeanFactory getBeanFactory(){
		/**
		 * BeanFactory：可以理解为IoC容器的抽象，提供了ioc容器的最基本API。
		 * ApplicationContext：IoC容器的高级形态，在基础IoC容器上加了许多特性。
		 * DefaultListableBeanFactory是IoC容器的最基础实现，是一个最基础最简单的IoC容器对象，
		 * 其高级容器ApplicationContext也是通过持有DefaultListableBeanFactory引用，在基础IoC容器之上进行特性增强
		 **/
		// ApplicationContext context = new ClassPathXmlApplicationContext("bean.xml");
		// A a =  (A)context.getBean("a");
		// System.out.println(a.getB());

		ClassPathResource resource = new ClassPathResource("bean.xml");

		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);

		//这里设置了的话会在加载xml时就将  beandefinition的  beanClass从 String - > Class
		//reader.setBeanClassLoader(this.getClass().getClassLoader());

		reader.loadBeanDefinitions(resource);

		return factory;
	}

	static void aliasTest(){

		DefaultListableBeanFactory factory = getBeanFactory();

		BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder .genericBeanDefinition(HelloWorldTwo.class);

		factory.registerBeanDefinition("b", beanDefinitionBuilder.getBeanDefinition());

		Object b = factory.getBean("b");
		System.out.println(b);
		// b->B
	}

	public void argsTest(){
		// 查看传递args，单例子是否依旧生效
		DefaultListableBeanFactory factory = getBeanFactory();

		Object b = factory.getBean("b");
		Object b1 = factory.getBean("b",2,"2");
		System.out.println(b);
		System.out.println(b1);
		System.out.println(b==b1);

		//========
		//B{i=1, string='2'}
		//B{i=1, string='2'}
		//true
	}


	public static void testAutoWired(){
		DefaultListableBeanFactory factory = getBeanFactory();

		Object b = factory.getBean("c");

		System.out.println(b);

	}



}
