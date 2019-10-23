/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Utility class for working with Strings that have placeholder values in them. A placeholder takes the form
 * {@code ${name}}. Using {@code PropertyPlaceholderHelper} these placeholders can be substituted for
 * user-supplied values. <p> Values for substitution can be supplied using a {@link Properties} instance or
 * using a {@link PlaceholderResolver}.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 3.0
 */
public class PropertyPlaceholderHelper {

	private static final Log logger = LogFactory.getLog(PropertyPlaceholderHelper.class);

	private static final Map<String, String> wellKnownSimplePrefixes = new HashMap<>(4);

	static {
		wellKnownSimplePrefixes.put("}", "{");
		wellKnownSimplePrefixes.put("]", "[");
		wellKnownSimplePrefixes.put(")", "(");
	}


	private final String placeholderPrefix;

	private final String placeholderSuffix;

	private final String simplePrefix;

	@Nullable
	private final String valueSeparator;

	private final boolean ignoreUnresolvablePlaceholders;


	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * Unresolvable placeholders are ignored.
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 */
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix) {
		this(placeholderPrefix, placeholderSuffix, null, true);
	}

	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 * @param valueSeparator the separating character between the placeholder variable
	 * and the associated default value, if any
	 * @param ignoreUnresolvablePlaceholders indicates whether unresolvable placeholders should
	 * be ignored ({@code true}) or cause an exception ({@code false})
	 */
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix,
			@Nullable String valueSeparator, boolean ignoreUnresolvablePlaceholders) {

		Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
		Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
		this.placeholderPrefix = placeholderPrefix;
		this.placeholderSuffix = placeholderSuffix;
		String simplePrefixForSuffix = wellKnownSimplePrefixes.get(this.placeholderSuffix);
		if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
			this.simplePrefix = simplePrefixForSuffix;
		}
		else {
			this.simplePrefix = this.placeholderPrefix;
		}
		this.valueSeparator = valueSeparator;
		this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
	}


	/**
	 * Replaces all placeholders of format {@code ${name}} with the corresponding
	 * property from the supplied {@link Properties}.
	 * @param value the value containing the placeholders to be replaced
	 * @param properties the {@code Properties} to use for replacement
	 * @return the supplied value with placeholders replaced inline
	 */
	public String replacePlaceholders(String value, final Properties properties) {
		Assert.notNull(properties, "'properties' must not be null");
		return replacePlaceholders(value, properties::getProperty);
	}

	/**
	 * 替换格式为{@code $ {name}}的所有占位符。
	 * 使用提供的{@link PlaceholderResolver}中返回的值
	 *
	 * Replaces all placeholders of format {@code ${name}} with the value returned
	 * from the supplied {@link PlaceholderResolver}.
	 * @param value the value containing the placeholders to be replaced 包含要替换的占位符的值
	 * @param placeholderResolver the {@code PlaceholderResolver} to use for replacement props在里面
	 * @return the supplied value with placeholders replaced inline 返回真正的值
	 */
	public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
		Assert.notNull(value, "'value' must not be null");
		return parseStringValue(value, placeholderResolver, new HashSet<>());
	}

	protected String parseStringValue(String value, PlaceholderResolver placeholderResolver, Set<String> visitedPlaceholders) {
		// 11111${cn.chengl.test}
		StringBuilder result = new StringBuilder(value);

        // 获取前缀 "${" 的索引位置
		int startIndex = value.indexOf(this.placeholderPrefix);
		while (startIndex != -1) {
            // 获取 后缀 "}" 的索引位置
			int endIndex = findPlaceholderEndIndex(result, startIndex);
			if (endIndex != -1) {

                // 截取 "${" 和 "}" 中间的内容，这也就是我们在xml文件中<property name="str" value="${cn.chengl.test}"/>的cn.chengl.test
				String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);

				// 保存到set里防止循环引用 如${cn.chenglei} cn.chenglei=${cn.chenglei}，（下一轮会就会抛异常，此轮不会）
				String originalPlaceholder = placeholder;
				// set存在就抛异常
				if (!visitedPlaceholders.add(originalPlaceholder)) {
					throw new IllegalArgumentException(
							"Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
				}

                // 解析占位符键中包含的占位符，递归调用将【XML】文件中的占位符解析出来
				placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);

                // 将上面解析出的变量名 从 Properties 中获取对应的值 propVal
                String propVal                                                                                                                                                               = placeholderResolver.resolvePlaceholder(placeholder);

                // 如果不存在 ${mongodb.url:127.0.0.1}这种表达式 前面是变量名，后面是没取到的默认值
				if (propVal == null && this.valueSeparator != null) {
                    // 查询 : 的位置
					int separatorIndex = placeholder.indexOf(this.valueSeparator);
                    // 如果存在 :
					if (separatorIndex != -1) {
                        // 获取 : 前面部分 actualPlaceholder
						String actualPlaceholder = placeholder.substring(0, separatorIndex);
                        // 获取 : 后面部分 defaultValue
						String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
                        // 从 Properties 中获取 actualPlaceholder 对应的值
						propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
                        // 如果不存在 则返回 defaultValue
						if (propVal == null) {
							propVal = defaultValue;
						}
					}

				}
				if (propVal != null) {
					//再次递归解析，props文件中可能这样配  cn.chenglei=${cn.chenglei}, proVal=${cn.chenglei}
					propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
					//将11111${cn.chengl.test}中${cn.chengl.test}替换
					result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
					if (logger.isTraceEnabled()) {
						logger.trace("Resolved placeholder '" + placeholder + "'");
					}
					//11111${cn.chengl.test}${cn.chengl.test},外层大的while就是来弄后面多个$的
					startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
				} else if (this.ignoreUnresolvablePlaceholders) {
					// Proceed with unprocessed value.
                    // 忽略值
					startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
				} else {
					throw new IllegalArgumentException("Could not resolve placeholder '" +
							placeholder + "'" + " in value \"" + value + "\"");
				}
				visitedPlaceholders.remove(originalPlaceholder);
			} else {
				startIndex = -1;
			}
		}

        // 返回propVal，就是替换之后的值
		return result.toString();
	}

	private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
		int index = startIndex + this.placeholderPrefix.length();
		int withinNestedPlaceholder = 0;
		while (index < buf.length()) {
			if (StringUtils.substringMatch(buf, index, this.placeholderSuffix)) {
				if (withinNestedPlaceholder > 0) {
					withinNestedPlaceholder--;
					index = index + this.placeholderSuffix.length();
				}
				else {
					return index;
				}
			}
			else if (StringUtils.substringMatch(buf, index, this.simplePrefix)) {
				withinNestedPlaceholder++;
				index = index + this.simplePrefix.length();
			}
			else {
				index++;
			}
		}
		return -1;
	}


	/**
	 * Strategy interface used to resolve replacement values for placeholders contained in Strings.
	 */
	@FunctionalInterface
	public interface PlaceholderResolver {

		/**
		 * Resolve the supplied placeholder name to the replacement value.
		 * @param placeholderName the name of the placeholder to resolve
		 * @return the replacement value, or {@code null} if no replacement is to be made
		 */
		@Nullable
		String resolvePlaceholder(String placeholderName);
	}

}
