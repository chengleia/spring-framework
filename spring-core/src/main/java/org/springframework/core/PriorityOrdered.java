/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.core;

/**
 * Extension of the {@link Ordered} interface, expressing a <em>priority</em>
 * ordering: order values expressed by {@code PriorityOrdered} objects
 * always apply before same order values expressed by <em>plain</em>
 * {@link Ordered} objects.
 *
 * <p>This is primarily a special-purpose interface, used for objects where
 * it is particularly important to recognize <em>prioritized</em> objects
 * first, without even obtaining the remaining objects. A typical example:
 * prioritized post-processors in a Spring
 * {@link org.springframework.context.ApplicationContext}.
 *
 * <p>Note: {@code PriorityOrdered} post-processor beans are initialized in
 * a special phase, ahead of other post-processor beans. This subtly
 * affects their autowiring behavior: they will only be autowired against
 * beans which do not require eager initialization for type matching.
 *
 * 扩展{@link Ordered}接口，表示<em> priority </ em>
 * 排序：由{@code PriorityOrdered}对象表示的订单值
 * 始终先于<em> plain </ em表示的相同订单值>
 *  {@link Ordered}对象。
 *
 *  <p>这主要是一个特殊用途的接口，用于对象，其中
 * 首先识别<em>优先处理的</ em>对象*甚至不获取剩余对象，这一点尤其重要。一个典型的例子：
 * 在Spring中优先处理后处理器* {@link org.springframework.context.ApplicationContext}。
 *
 *  <p>注意：{@code PriorityOrdered}后处理器Bean在一个特殊阶段中被初始化，比其他后处理器Bean提前。
 *  巧妙地影响了它们的自动装配行为：仅将它们与bean进行自动装配，而bean不需要为类型匹配而急切初始化。
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.beans.factory.config.PropertyOverrideConfigurer
 * @see org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
 */
public interface PriorityOrdered extends Ordered {

}
