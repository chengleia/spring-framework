/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import org.apache.commons.logging.Log;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.*;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import java.beans.ConstructorProperties;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * Delegate for resolving constructors and factory methods.
 * Performs constructor resolution through argument matching.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Sebastien Deleuze
 * @since 2.0
 * @see #autowireConstructor
 * @see #instantiateUsingFactoryMethod
 * @see AbstractAutowireCapableBeanFactory
 */
class ConstructorResolver {

	private static final Object[] EMPTY_ARGS = new Object[0];

	private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint =
			new NamedThreadLocal<>("Current injection point");

	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final Log logger;


	/**
	 * Create a new ConstructorResolver for the given factory and instantiation strategy.
	 * @param beanFactory the BeanFactory to work with
	 */
	public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.logger = beanFactory.getLogger();
	}


	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param chosenCtors chosen candidate constructors (or {@code null} if none)
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {
        // 封装 BeanWrapperImpl  并完成初始化
		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);

		// 获得 constructorToUse、argsHolderToUse、argsToUse
		Constructor<?> constructorToUse = null; // 构造函数
		ArgumentsHolder argsHolderToUse = null; // 构造参数
		Object[] argsToUse = null; // 构造参数

        // 确定构造参数
        // 如果 getBean() 已经传递，则直接使用
		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		} else {
		    // 尝试从缓存中获取
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
                // 缓存中的构造函数或者工厂方法
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
                    // 缓存中的构造参数
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
            // 缓存中存在,则解析存储在 BeanDefinition 中的参数
            // 如给定方法的构造函数 A(int ,int )，则通过此方法后就会把配置文件中的("1","1")转换为 (1,1)
            // 缓存中的值可能是原始值也有可能是最终值
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}

		// 没有缓存，则尝试从配置文件中获取参数
		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
            // 如果 chosenCtors 未传入，则获取构造方法们 candidates候选的意思
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					candidates = (mbd.isNonPublicAccessAllowed() ? beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				} catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}

            if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				// 这里只有一个构造器，且无参我们直接创建
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					// 无参构造器+空参数EMPTY_ARGS
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

            // 如果传入构造器不为空，或者 是构造器注入模式. 即使没有mbd.hasConstructorArgumentValues()我们也得去spring给他找。来个标记位
			// Need to resolve the constructor.
			boolean autowiring = (chosenCtors != null || mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

            // 主要存放了参数值和参数值所对应的下标
			ConstructorArgumentValues resolvedValues = null;
			int minNrOfArgs;
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			} else {
                // 从 BeanDefinition 中获取构造参数，也就是从配置文件中提取构造参数
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				// 解析后的下面逻辑用它
				resolvedValues = new ConstructorArgumentValues();

                // 进行解析，resolvedValues里面已经是解析完了值 ref->object。
                minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}

            // 对构造函数进行排序处理 怎么排序的呢？
			/**
			 *  先访问权限，然后参数个数
			 * 1. public Test(Object o1, Object o2, Object o3)
			 * 2. public Test(Object o1, Object o2)
			 * 3. public Test(Object o1)
			 * 4. protected Test(Integer i, Object o1, Object o2, Object o3)
			 * 5. protected Test(Integer i, Object o1, Object o2)
			 * 6. protected Test(Integer i, Object o1)
			 */
			AutowireUtils.sortConstructors(candidates);

            // 权重差异变量
			int minTypeDiffWeight = Integer.MAX_VALUE;

			// 不确定的构造器
			Set<Constructor<?>> ambiguousConstructors = null;
			LinkedList<UnsatisfiedDependencyException> causes = null;

            // 迭代所有构造函数
			for (Constructor<?> candidate : candidates) {
                // 获取该构造函数的参数类型
				Class<?>[] paramTypes = candidate.getParameterTypes();

                // 如果已经找到选用的构造函数或者需要的参数个数小于当前的构造函数参数个数，则终止。
                // 因为，已经按照参数个数降序排列了
				if (constructorToUse != null && argsToUse.length > paramTypes.length) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					break;
				}

                // 构造器参数个数小于的我需要的个数直接跳过这个构造器
				if (paramTypes.length < minNrOfArgs) {
					continue;
				}

                // 参数持有者 ArgumentsHolder 对象
				ArgumentsHolder argsHolder;
				if (resolvedValues != null) {
					try {

                        // ConstructorProperties注释上获取参数名称，这个注解上名字个数一定要等于参数个数，不然抛错
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, paramTypes.length);

						// 注解拿不到用JDK8的特性去获取
						if (paramNames == null) {
                            // 获取构造函数、方法参数的探测器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								//获取构造方法参数名称列表
								// 还记得我们看Mybatis时说的jdk8以前不能取到方法名吗ParameterNameDiscoverer这套东西就是jdk8之后出得，如果我们编译没开-parameters依旧取不到
								/**
								 * 假设你有一个（String aaa,Object vvv）
								 * 则paramNames=[aaa,vvv]
								 */
								paramNames = pnd.getParameterNames(candidate);
							}
						}

                        // 根据构造函数和构造参数，创建参数持有者 ArgumentsHolder 对象
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);

					} catch (UnsatisfiedDependencyException ex) {
                        // 若发生 UnsatisfiedDependencyException 异常，添加到 causes 中。
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						if (causes == null) {
							causes = new LinkedList<>();
						}
						causes.add(ex);
						continue; // continue ，继续执行，换下一个
					}

				} else {
                    // resolvedValues = null 就是getbean传了参数
					// Explicit arguments given -> arguments length must match exactly.
					if (paramTypes.length != explicitArgs.length) {
						continue;
					}
					// 根据 explicitArgs ，创建 ArgumentsHolder 对象
					argsHolder = new ArgumentsHolder(explicitArgs);
				}

                // isLenientConstructorResolution 判断解析构造函数的时候是否以宽松模式还是严格模式
                // 严格模式：解析构造函数时，必须所有的都需要匹配，否则抛出异常
                // 宽松模式：使用具有"最接近的模式"进行匹配
                // typeDiffWeight：类型差异权重
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ? argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));

				// Choose this constructor if it represents the closest match.
                // 如果它代表着当前最接近的匹配则选择其作为构造函数
				if (typeDiffWeight < minTypeDiffWeight) {
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				} else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}

            // 没有可执行的工厂方法，抛出异常
			if (constructorToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			} else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			if (explicitArgs == null) {
                // 将解析的构造函数加入缓存
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

        // 创建 Bean 对象，并设置到 bw 中
        bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		return bw;
	}

	private Object instantiate(String beanName, RootBeanDefinition mbd, Constructor constructorToUse, Object[] argsToUse) {
		try {
		    // 获得 InstantiationStrategy 对象
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			// 安全模式
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse),
						this.beanFactory.getAccessControlContext());
			} else {
				return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}
		} catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}

	/**
	 * Resolve the factory method in the specified bean definition, if possible.
	 * {@link RootBeanDefinition#getResolvedFactoryMethod()} can be checked for the result.
	 * @param mbd the bean definition to check
	 */
	public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
		Class<?> factoryClass;
		boolean isStatic;
		if (mbd.getFactoryBeanName() != null) {
			factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
			isStatic = false;
		}
		else {
			factoryClass = mbd.getBeanClass();
			isStatic = true;
		}
		Assert.state(factoryClass != null, "Unresolvable factory class");
		factoryClass = ClassUtils.getUserClass(factoryClass);

		Method[] candidates = getCandidateMethods(factoryClass, mbd);
		Method uniqueCandidate = null;
		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				if (uniqueCandidate == null) {
					uniqueCandidate = candidate;
				}
				else if (!Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes())) {
					uniqueCandidate = null;
					break;
				}
			}
		}
		synchronized (mbd.constructorArgumentLock) {
			mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
		}
	}

	/**
	 * Retrieve all candidate methods for the given class, considering
	 * the {@link RootBeanDefinition#isNonPublicAccessAllowed()} flag.
	 * Called as the starting point for factory method determination.
	 */
	private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
					(mbd.isNonPublicAccessAllowed() ?
						ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
		}
		else {
			return (mbd.isNonPublicAccessAllowed() ?
					ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * bean definition parameter specifies a class, rather than a "factory-bean", or
	 * an instance variable on a factory object itself configured using Dependency Injection.
	 * <p>Implementation requires iterating over the static or instance methods with the
	 * name specified in the RootBeanDefinition (the method may be overloaded) and trying
	 * to match with the parameters. We don't have the types attached to constructor args,
	 * so trial and error is the only way to go here. The explicitArgs array may contain
	 * argument values passed in programmatically via the corresponding getBean method.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean
	 * method, or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	public BeanWrapper instantiateUsingFactoryMethod( String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {
		//  https://blog.csdn.net/weixin_38816358/article/details/83789784 实例和静态工厂创建

		// 构造 BeanWrapperImpl 对象
		BeanWrapperImpl bw = new BeanWrapperImpl();

        // 初始化 BeanWrapperImpl
        // 向BeanWrapper对象中添加 ConversionService 对象和属性编辑器 PropertyEditor 对象
        this.beanFactory.initBeanWrapper(bw);

        // 获得  factoryBean、
		//      factoryClass、
		//      isStatic、
		//      factoryBeanName
		Object factoryBean;
		Class<?> factoryClass;
		boolean isStatic;
		String factoryBeanName = mbd.getFactoryBeanName();

		// 工厂名不为空
        if (factoryBeanName != null) {
			if (factoryBeanName.equals(beanName)) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}

            // 获取工厂实例
			factoryBean = this.beanFactory.getBean(factoryBeanName);

			// 是单例 以创建了又跑过来创建
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
				throw new ImplicitlyAppearedSingletonException();
			}

			factoryClass = factoryBean.getClass();

			isStatic = false;

		} else {

            // 工厂名为空，则其可能是一个静态工厂
            // 静态工厂创建bean，必须要提供工厂的全类名
			// It's a static factory method on the bean class.
			if (!mbd.hasBeanClass()) {
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}

			factoryBean = null;

			factoryClass = mbd.getBeanClass();

			isStatic = true;
		}

		// 获得 factoryMethodToUse、argsHolderToUse、argsToUse 属性
		Method factoryMethodToUse = null;  // 工厂方法

		ArgumentsHolder argsHolderToUse = null;

		Object[] argsToUse = null; // 参数

        // 如果指定了构造参数则直接使用
        // 在调用 getBean 方法的时候指定了方法参数
		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		} else {
            // 没有指定，则尝试从配置文件中解析
			Object[] argsToResolve = null;

            // 首先尝试从缓存中获取
			synchronized (mbd.constructorArgumentLock) {
                // 获取缓存中的构造函数或者工厂方法
				factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;

				if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached factory method...
                    // 获取缓存中的构造参数
					argsToUse = mbd.resolvedConstructorArguments;

					if (argsToUse == null) {
                        // 获取缓存中的构造函数参数的包可见字段
						argsToResolve = mbd.preparedConstructorArguments;
					}

				}
			}
            // 缓存中存在,则解析存储在 BeanDefinition 中的参数
            // 如给定方法的构造函数 A(int ,int )，则通过此方法后就会把配置文件中的("1","1")转换为 (1,1)
            // 缓存中的值可能是原始值也有可能是最终值
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve, true);
			}

		}

		if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
            // 获取工厂方法的类全名称
			factoryClass = ClassUtils.getUserClass(factoryClass);

            // 获取所有待定方法
			Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);

            // 检索所有方法，这里是对方法进行过滤
			List<Method> candidateList = new ArrayList<>();

			for (Method candidate : rawCandidates) {
                // 如果有static 且为工厂方法，则添加到 candidateSet 中
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
					candidateList.add(candidate);
				}
			}

			// TODO 芋艿 创建 Bean
			if (candidateList.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Method uniqueCandidate = candidateList.get(0);
				if (uniqueCandidate.getParameterCount() == 0) {
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}


			Method[] candidates = candidateList.toArray(new Method[0]);
            // 排序构造函数
            // public 构造函数优先参数数量降序，非 public 构造函数参数数量降序
			AutowireUtils.sortFactoryMethods(candidates);

            // 用于承载解析后的构造函数参数的值
			ConstructorArgumentValues resolvedValues = null;
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Method> ambiguousFactoryMethods = null;

			int minNrOfArgs;
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			} else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
                // getBean() 没有传递参数，则需要解析保存在 BeanDefinition 构造函数中指定的参数
				if (mbd.hasConstructorArgumentValues()) {
                    // 构造函数的参数
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
					resolvedValues = new ConstructorArgumentValues();
                    // 解析构造函数的参数
                    // 将该 bean 的构造函数参数解析为 resolvedValues 对象，其中会涉及到其他 bean
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
				} else {
					minNrOfArgs = 0;
				}
			}

			LinkedList<UnsatisfiedDependencyException> causes = null; // 记录 UnsatisfiedDependencyException 异常的集合
			// 遍历 candidates 数组
			for (Method candidate : candidates) {
                // 方法体的参数
				Class<?>[] paramTypes = candidate.getParameterTypes();

				if (paramTypes.length >= minNrOfArgs) {
                    // 保存参数的对象
					ArgumentsHolder argsHolder;

                    // #getBean(...) 传递了参数
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
                        // 显示给定参数，参数长度必须完全匹配
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}
                        // 根据参数创建参数持有者 ArgumentsHolder 对象
						argsHolder = new ArgumentsHolder(explicitArgs);
					} else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary.
                        // 为提供参数，解析构造参数
                        try {
							String[] paramNames = null;
                            // 获取 ParameterNameDiscoverer 对象
                            // ParameterNameDiscoverer 是用于解析方法和构造函数的参数名称的接口，为参数名称探测器
                            ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
                            // 获取指定构造函数的参数名称
							if (pnd != null) {
								paramNames = pnd.getParameterNames(candidate);
							}
                            // 在已经解析的构造函数参数值的情况下，创建一个参数持有者 ArgumentsHolder 对象
                            argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.length == 1);
						} catch (UnsatisfiedDependencyException ex) {
							// 若发生 UnsatisfiedDependencyException 异常，添加到 causes 中。
                            if (logger.isTraceEnabled()) {
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method.
							if (causes == null) {
								causes = new LinkedList<>();
							}
							causes.add(ex);
							continue; // continue ，继续执行
						}
					}

                    // isLenientConstructorResolution 判断解析构造函数的时候是否以宽松模式还是严格模式
                    // 严格模式：解析构造函数时，必须所有的都需要匹配，否则抛出异常
                    // 宽松模式：使用具有"最接近的模式"进行匹配
                    // typeDiffWeight：类型差异权重
					int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
					// Choose this factory method if it represents the closest match.
                    // 代表最接近的类型匹配，则选择作为构造函数
					if (typeDiffWeight < minTypeDiffWeight) {
						factoryMethodToUse = candidate;
						argsHolderToUse = argsHolder;
						argsToUse = argsHolder.arguments;
						minTypeDiffWeight = typeDiffWeight;
						ambiguousFactoryMethods = null;
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
                    // 如果具有相同参数数量的方法具有相同的类型差异权重，则收集此类型选项
                    // 但是，仅在非宽松构造函数解析模式下执行该检查，并显式忽略重写方法（具有相同的参数签名）
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
                        // 查找到多个可匹配的方法
					    if (ambiguousFactoryMethods == null) {
							ambiguousFactoryMethods = new LinkedHashSet<>();
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}

            // 没有可执行的工厂方法，抛出异常
			if (factoryMethodToUse == null) {
				if (causes != null) {
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				if (explicitArgs != null) {
					for (Object arg : explicitArgs) {
						argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
					}
				} else if (resolvedValues != null) {
					Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
					valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
					valueHolders.addAll(resolvedValues.getGenericArgumentValues());
					for (ValueHolder value : valueHolders) {
						String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
								(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
						argTypes.add(argType);
					}
				}
				String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"No matching factory method found: " +
						(mbd.getFactoryBeanName() != null ?
							"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
						"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
						"Check that a method with the specified name " +
						(minNrOfArgs > 0 ? "and arguments " : "") +
						"exists and that it is " +
						(isStatic ? "static" : "non-static") + ".");
			} else if (void.class == factoryMethodToUse.getReturnType()) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid factory method '" + mbd.getFactoryMethodName() +
						"': needs to have a non-void return type!");
			} else if (ambiguousFactoryMethods != null) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousFactoryMethods);
			}

			if (explicitArgs == null && argsHolderToUse != null) {
                // 将解析的构造函数加入缓存
				argsHolderToUse.storeCache(mbd, factoryMethodToUse);
			}
		}

		// 创建 Bean 对象，并设置到 bw 中
		bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
		return bw;
	}

	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Object factoryBean, Method factoryMethod, Object[] args) {

		try {
		    // 安全模式
			if (System.getSecurityManager() != null) {
                // 通过执行工厂方法来创建bean示例
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						this.beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
						this.beanFactory.getAccessControlContext());
			} else {
                // 通过执行工厂方法来创建bean示例
				return this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
			}
		} catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}

	/**
	 * Resolve the constructor arguments for this bean into the resolvedValues object.
	 * This may involve looking up other beans.
	 * <p>This method is also used for handling invocations of static factory methods.
	 */
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw, ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {

		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);

		// 最小构造函数参数个数 等于有index+没有index制定的构造函数参数
		int minNrOfArgs = cargs.getArgumentCount();

		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
			int index = entry.getKey();
			if (index < 0) {
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}
			//假如我制定index=100，说明我是第101个参数，所以最小参数minNrOfArgs等于101
			if (index > minNrOfArgs) {
				minNrOfArgs = index + 1;
			}

			// 没转换的话进行转换一下 及ref的话，拿到实际对象
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
			if (valueHolder.isConverted()) {
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			} else {
				Object resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				resolvedValueHolder.setSource(valueHolder);
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
			// 没制定index的只进行转化
			if (valueHolder.isConverted()) {
				resolvedValues.addGenericArgumentValue(valueHolder);
			}else {
				Object resolvedValue = valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				resolvedValueHolder.setSource(valueHolder);
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}

		return minNrOfArgs;
	}

	/**
	 * Create an array of arguments to invoke a constructor or factory method,
	 * given the resolved constructor argument values.
	 */
	private ArgumentsHolder createArgumentArray( String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {

		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);

		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);


		//遍历这个构造器参数
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {

			Class<?> paramType = paramTypes[paramIndex];
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");
			// Try to find matching constructor argument value, either indexed or generic.
			ConstructorArgumentValues.ValueHolder valueHolder = null;

			//xml里定义了传给构造方法的参数
			if (resolvedValues != null) {
				//构造器定义参数从 传给构造方法的参数。选取一个合适的
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);


				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				//没有选取到 且 不是自动出入或者 构造器定义参数和传给构造方法的参数个数一样
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					// <1>直接就按顺序给 不一定能转成功 String -> int能成功
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}

			if (valueHolder != null) {

				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
				// 添加已使用
				usedValueHolders.add(valueHolder);
				// 原来的值，如果是<1>状况就得转化
				Object originalValue = valueHolder.getValue();
				// 转化的值
				Object convertedValue;

				// 是否被转化过 他好像在这里不会被转化。 比较是xml里定义的 不能在我这尝试就被转化了
				if (valueHolder.isConverted()) {
					// 是就记录转化的值。 ArgumentsHolder对应index保存转化后的
					convertedValue = valueHolder.getConvertedValue();
					args.preparedArguments[paramIndex] = convertedValue;
				} else {
					//没被转化
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						//进行转化 明确知道string有转化， ref会在这里转化吗？
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					} catch (TypeMismatchException ex) {
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}

					//valueHolder如果是copy方法靠背出来的Source对象
					Object sourceHolder = valueHolder.getSource();
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						// 需要解析
						args.resolveNecessary = true;
						// preparedArguments存源信息
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}

				// 使用的 记录程程转化后的
				args.arguments[paramIndex] = convertedValue;

				// 源数据 转化之前的
				args.rawArguments[paramIndex] = originalValue;

			} else {
				// 找不不到合适传过来的参数
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
				// 不是自动出入，抛异常因为构造方法没有合适参数
				if (!autowiring) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}

				// 是自动注入
				try {
					// 到spring里面去找了。
					Object autowiredArgument = resolveAutowiredArgument( methodParam, beanName, autowiredBeanNames, converter, fallback);

					// 原是我们从spring取的
					args.rawArguments[paramIndex] = autowiredArgument;
					// 用的也是我们spring取的
					args.arguments[paramIndex] = autowiredArgument;
					// 一个空类，只是做标记
					args.preparedArguments[paramIndex] = new AutowiredArgumentMarker();

					// 参数需要解析标记为true
					args.resolveNecessary = true;

				}catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}

			}
		}

		for (String autowiredBeanName : autowiredBeanNames) {
			//向Spring声明下依赖关系
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}

		return args;
	}

	/**
	 * Resolve the prepared arguments stored in the given bean definition.
	 */
	private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			Executable executable, Object[] argsToResolve, boolean fallback) {

		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		Class<?>[] paramTypes = executable.getParameterTypes();

		Object[] resolvedArgs = new Object[argsToResolve.length];
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			Object argValue = argsToResolve[argIndex];
			MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);
			GenericTypeResolver.resolveParameterType(methodParam, executable.getDeclaringClass());
			if (argValue instanceof AutowiredArgumentMarker) {
				argValue = resolveAutowiredArgument(methodParam, beanName, null, converter, fallback);
			}
			else if (argValue instanceof BeanMetadataElement) {
				argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
			}
			else if (argValue instanceof String) {
				argValue = this.beanFactory.evaluateBeanDefinitionString((String) argValue, mbd);
			}
			Class<?> paramType = paramTypes[argIndex];
			try {
				resolvedArgs[argIndex] = converter.convertIfNecessary(argValue, paramType, methodParam);
			}
			catch (TypeMismatchException ex) {
				throw new UnsatisfiedDependencyException(
						mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
						"Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue) +
						"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		return resolvedArgs;
	}

	protected Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
		Class<?> declaringClass = constructor.getDeclaringClass();
		Class<?> userClass = ClassUtils.getUserClass(declaringClass);
		if (userClass != declaringClass) {
			try {
				return userClass.getDeclaredConstructor(constructor.getParameterTypes());
			}
			catch (NoSuchMethodException ex) {
				// No equivalent constructor on user class (superclass)...
				// Let's proceed with the given constructor as we usually would.
			}
		}
		return constructor;
	}

	/**
	 * Template method for resolving the specified argument which is supposed to be autowired.
	 */
	@Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {

		Class<?> paramType = param.getParameterType();
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			if (injectionPoint == null) {
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			return injectionPoint;
		}
		try {
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw ex;
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				if (paramType.isArray()) {
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					return CollectionFactory.createCollection(paramType, 0);
				}
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			throw ex;
		}
	}

	static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {
		InjectionPoint old = currentInjectionPoint.get();
		if (injectionPoint != null) {
			currentInjectionPoint.set(injectionPoint);
		}
		else {
			currentInjectionPoint.remove();
		}
		return old;
	}


	/**
	 * Private inner class for holding argument combinations.
	 */
	private static class ArgumentsHolder {

		public final Object[] rawArguments;

		public final Object[] arguments;

		public final Object[] preparedArguments;

		public boolean resolveNecessary = false;

		public ArgumentsHolder(int size) {
			this.rawArguments = new Object[size];
			this.arguments = new Object[size];
			this.preparedArguments = new Object[size];
		}

		public ArgumentsHolder(Object[] args) {
			this.rawArguments = args;
			this.arguments = args;
			this.preparedArguments = args;
		}

		public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
			// If valid arguments found, determine type difference weight.
			// Try type difference weight on both the converted arguments and
			// the raw arguments. If the raw weight is better, use it.
			// Decrease raw weight by 1024 to prefer it over equal converted weight.
			// 如果找到有效的参数，请确定类型差异权重。
			// 在转换后的参数和原始参数上尝试使用类型差异权重。如果原始重量更好，请使用它。
			// 将原始重量减少1024，以使其优先于相等的转换重量。
			int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);
			int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;
			return (rawTypeDiffWeight < typeDiffWeight ? rawTypeDiffWeight : typeDiffWeight);
		}

		public int getAssignabilityWeight(Class<?>[] paramTypes) {
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {
					return Integer.MAX_VALUE;
				}
			}
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {
					return Integer.MAX_VALUE - 512;
				}
			}
			return Integer.MAX_VALUE - 1024;
		}

		public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
			synchronized (mbd.constructorArgumentLock) {
				mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
				mbd.constructorArgumentsResolved = true;
				if (this.resolveNecessary) {
					mbd.preparedConstructorArguments = this.preparedArguments;
				} else {
					mbd.resolvedConstructorArguments = this.arguments;
				}
			}
		}
	}


	/**
	 * Marker for autowired arguments in a cached argument array.
 	 */
	private static class AutowiredArgumentMarker {
	}


	/**
	 * Delegate for checking Java 6's {@link ConstructorProperties} annotation.
	 */
	private static class ConstructorPropertiesChecker {

		@Nullable
		public static String[] evaluate(Constructor<?> candidate, int paramCount) {
			ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
			if (cp != null) {
				String[] names = cp.value();
				if (names.length != paramCount) {
					throw new IllegalStateException("Constructor annotated with @ConstructorProperties but not " +
							"corresponding to actual number of parameters (" + paramCount + "): " + candidate);
				}
				return names;
			}
			else {
				return null;
			}
		}
	}

}
