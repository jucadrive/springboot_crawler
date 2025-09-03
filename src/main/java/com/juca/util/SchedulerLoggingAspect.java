package com.juca.util; // 패키지 경로를 프로젝트에 맞게 수정해주세요.

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
// LogUtil을 사용하기 위해 임포트


@Aspect
@Component
public class SchedulerLoggingAspect { // 클래스 이름을 LoggingAspect에서 SchedulerLoggingAspect로 변경

    // 로깅은 LogUtil에서 담당하므로, 여기서는 LogUtil을 호출하는 형태로 구성합니다.
    // 만약 SchedulerLoggingAspect 내부에서 별도의 로깅 포맷이 필요하다면 아래 Logger를 사용해도 무방합니다.
    // private static final Logger logger = LoggerFactory.getLogger(SchedulerLoggingAspect.class);

    // 주의: HttpServletRequest는 웹 요청 스코프에서만 사용 가능합니다.
    // 스케줄러는 웹 요청과 무관하게 동작하므로 제거해야 합니다.
    // private final HttpServletRequest request; // 이 필드를 제거합니다.

    // @Around("execution(* com.jhc.jhc_geocoding.controller..*(..))")
    // 위 표현식은 컨트롤러에 대한 것이므로, 스케줄러 클래스의 메서드를 대상으로 변경해야 합니다.
    // 예를 들어, CrawlingScheduler 클래스의 startArticleCrawling() 메서드를 대상으로 하려면:
    @Around("execution(* com.juca.crawler.scheduler.CrawlingScheduler.startArticleCrawling(..))")
    public Object logSchedulerExecution(ProceedingJoinPoint joinPoint) throws Throwable {

        String schedulerName = joinPoint.getTarget().getClass().getSimpleName(); // 스케줄러 클래스 이름 (예: "CrawlingScheduler")
        String methodName = joinPoint.getSignature().getName(); // 실행되는 메서드 이름 (예: "startArticleCrawling")
        String arguments = Arrays.toString(joinPoint.getArgs()); // 메서드 인자들

        long startTime = System.currentTimeMillis(); // 실행 시간 측정을 위한 시작 시간

        // LogUtil을 사용하여 스케줄러 시작을 기록
        LogUtil.logSchedulerStart(schedulerName, methodName);
        LogUtil.logInfo(String.format("""
                        [SCHEDULER_REQUEST]
                        {
                          "scheduler": "%s",
                          "method": "%s",
                          "arguments": %s
                        }""",
                schedulerName,
                methodName,
                arguments
        ));

        Object result = null;
        try {
            // 실제 스케줄러 메서드 호출
            result = joinPoint.proceed();
            return result; // 결과 반환
        } catch (Throwable e) {
            // 예외 발생 시 LogUtil을 사용하여 기록
            LogUtil.logSchedulerException(schedulerName, methodName, e, "스케줄러 실행 중 예외 발생");
            throw e; // 예외를 다시 던져서 정상적인 예외 흐름을 유지
        } finally {
            // 스케줄러 메서드 호출 후 정보 기록 (성공/실패 무관)
            long duration = System.currentTimeMillis() - startTime;

            // LogUtil을 사용하여 스케줄러 완료를 기록
            // 결과 객체는 스케줄러 메서드에 따라 없을 수도 있고 (void), 특정 값을 반환할 수도 있습니다.
            // 여기서는 간단히 toString()으로 처리하거나, 필요시 커스텀 로직을 추가합니다.
            String resultStr = (result != null) ? result.toString() : "void/null";
            LogUtil.logSchedulerCompletion(schedulerName, methodName, "실행 완료");
            LogUtil.logInfo(String.format("""
                        [SCHEDULER_RESPONSE]
                        {
                          "scheduler": "%s",
                          "method": "%s",
                          "result": "%s",
                          "duration": "%dms"
                        }""",
                    schedulerName,
                    methodName,
                    resultStr,
                    duration
            ));
        }
    }
}