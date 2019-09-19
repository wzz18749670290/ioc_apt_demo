package com.wzz.demo.annotation_compiler;

import com.google.auto.service.AutoService;
import com.wzz.demo.annotations.BindContentView;
import com.wzz.demo.annotations.BindOnClick;
import com.wzz.demo.annotations.BindOnLongClick;
import com.wzz.demo.annotations.BindViewID;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

/**
 * 注解处理器
 *
 * @author wangzhenzhou
 * @createTime 2019-09-06 17:41
 */
@AutoService(Processor.class)
public class AnnotationCompiler extends AbstractProcessor {
    //定义用于生成文件的对象
    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mFiler = processingEnvironment.getFiler();
    }

    /**
     * 确定当前APT处理所有模块中的哪些注解
     *
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportAnnotationSet = new HashSet<>();
        supportAnnotationSet.add(BindContentView.class.getCanonicalName());
        supportAnnotationSet.add(BindOnClick.class.getCanonicalName());
        supportAnnotationSet.add(BindOnLongClick.class.getCanonicalName());
        supportAnnotationSet.add(BindViewID.class.getCanonicalName());
        return supportAnnotationSet;
    }

    /**
     * 支持的JDK版本
     *
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 该方法中生成IBinder的实现类
     *
     * @param set
     * @param roundEnvironment
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //Element
        //TypeElement:类元素
        //ExecutableElement:可执行元素
        //VariableElement:属性元素

        List<ActivityElement> activityElementList = ActivityElementUtils.collect(roundEnvironment);

        for (ActivityElement activityElement : activityElementList) {
            TypeElement             bindContentViewElement  = activityElement.getBindContentViewElement();
            List<VariableElement>   bindViewIDElements      = activityElement.getBindViewIDElements();
            List<ExecutableElement> bindOnClickElements     = activityElement.getBindOnClickElements();
            List<ExecutableElement> bindOnLongClickElements = activityElement.getBindOnLongClickElements();
            writeSingleActivity(bindContentViewElement, bindViewIDElements, bindOnClickElements, bindOnLongClickElements);

        }
        return false;
    }

    private void writeSingleActivity(TypeElement bindContentViewElement, List<VariableElement> bindViewIDElements,
                                     List<ExecutableElement> bindOnClickElements, List<ExecutableElement> bindOnLongClickElements) {
        Writer writer = null;
        try {
            String packageName  = processingEnv.getElementUtils().getPackageOf(bindContentViewElement).toString();
            String activityName = bindContentViewElement.getSimpleName().toString();

            //生成文件 packageName+.+activityName+_ViewBinding
            JavaFileObject sourceFile = mFiler.createSourceFile(packageName + "." + activityName + "_ViewBinding");
            //获取文件写入流
            writer = sourceFile.openWriter();
            //第一行 导包
            writer.write("package " + packageName + ";\n\n");
            //导包行
            writer.write("import com.wzz.demo.annotations.IBinder;\n\n");
            writer.write("import android.view.View;\n\n");
            //写入类定义
            writer.write("public class " + activityName + "_ViewBinding implements IBinder<" + packageName + "." + activityName + "> {\n");
            writer.write("\t@Override\n");
            writer.write("\tpublic void bind(final " + packageName + "." + activityName + " target) {\n");
            //写入activity.setContentView(layoutId)
            int layoutId = bindContentViewElement.getAnnotation(BindContentView.class).value();
            writer.write("\t\ttarget.setContentView(" + layoutId + ");\n");
            //bind方法中写入widget=findViewById(id)
            for (VariableElement variableElement : bindViewIDElements) {
                //获取控件变量名字
                String variableName = variableElement.getSimpleName().toString();
                //获取id
                int id = variableElement.getAnnotation(BindViewID.class).value();
                //获取控件变量的类型
                TypeMirror typeMirror = variableElement.asType();
                //写入 target.textView = (android.widget.TextView)target.findViewById(id)
                writer.write("\t\ttarget." + variableName + " = (" + typeMirror + ") target.findViewById(" + id + ");\n");
            }
            //bind方法中写入widget.setOnClickListener
            for (ExecutableElement bindOnClickElement : bindOnClickElements) {
                int[] ids = bindOnClickElement.getAnnotation(BindOnClick.class).value();
                for (int id : ids) {
                    writer.write("\t\ttarget.findViewById(" + id + ").setOnClickListener(new View.OnClickListener() {\n" +
                            "\t\t\t@Override\n" +
                            "\t\t\tpublic void onClick(View v) {\n" +
                            "\t\t\t\ttarget." + bindOnClickElement.getSimpleName() + "(v);\n"
                            + "\t\t\t}\n" +
                            "\t\t});\n"
                    );
                }
            }
            //bind方法中写入widget.setOnLongClickListener
            for (ExecutableElement bindOnLongClickElement : bindOnLongClickElements) {
                int[] ids = bindOnLongClickElement.getAnnotation(BindOnLongClick.class).value();
                for (int id : ids) {
                    writer.write("\t\ttarget.findViewById(" + id + ").setOnLongClickListener(new View.OnLongClickListener() {\n" +
                            "\t\t\t@Override\n" +
                            "\t\t\tpublic boolean onLongClick(View v) {\n" +
                            "\t\t\t\ttarget." + bindOnLongClickElement.getSimpleName() + "(v);\n"
                            + "\t\t\t\treturn true;\n"
                            + "\t\t\t}\n" +
                            "\t\t});\n"
                    );
                }
            }

            writer.write("\t}");
            writer.write("\n}");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
