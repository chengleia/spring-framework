package org.springframework.study;

import org.aspectj.lang.JoinPoint;

import java.util.Arrays;

public class TestAop {
	//第一个 * 代表任意修饰符及任意返回值,其中 .. 匹配任意数量的参数.
	public void methodBefore(JoinPoint joinpoint) {
		System.out.println("执行方法:" + joinpoint.getSignature().getName() + "之前" + " 参数：" + Arrays.asList(joinpoint.getArgs()));
	}

	//第一个 * 代表public修饰符下任意返回值，第一个 * 代表com.zr.utils.Calculate路径下的任意方法
	public void methodAfter(JoinPoint joinpoint) {
		System.out.println("执行方法:" + joinpoint.getSignature().getName() + "之后");
	}

	//匹配第一个参数为 int 类型的方法, .. 匹配任意数量任意类型的参数
	public void methodAfterRunning(JoinPoint joinpoint, Object result) {
		System.out.println("返回结果之后执行" + ",返回结果：" + result);
	}

	//匹配参数类型为 int, int 类型的方法.
	public void methodAfterThrowing(JoinPoint joinPoint, Exception exception) {
		System.out.println("异常通知, 在方法抛出异常之后执行" + ",异常日志:" + exception);
	}
}