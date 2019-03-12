package org.springframework.study;

import org.junit.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URL;

public class ResourceTest {

	@Test
	public void notPam() throws Exception{

		// 清理文件路径,这个方法配合applyRelativePath就可以计算一些简单的相对路径了
		// 打印:d:/java/other/Some.java
		//System.out.println(StringUtils.cleanPath("d:/java/wolfcode/../other/Some.java"));

		// 需求：获取d:/java/wolfcode/Test.java相对路径为../../other/Some.java的文件全路径：
		// 打印：d:/other/Some.java
		//System.out.println(StringUtils.cleanPath(StringUtils.applyRelativePath("d:/java/wolfcode/Test.java", "../../other/Some.java")));

		//没有无参构造函数
		//FileSystemResource fileSystemResource = new FileSystemResource("D://chenglei/dd.pdf");

		//UrlResource
		//UrlResource urlResource = new UrlResource("ftp://www.baidu.com/dd.pdf");
		UrlResource urlResource = new UrlResource(new URL("http://www.baidu.com/d d.pdf"));
		URI uri = urlResource.getURI();
		System.out.println("");

	}
}
