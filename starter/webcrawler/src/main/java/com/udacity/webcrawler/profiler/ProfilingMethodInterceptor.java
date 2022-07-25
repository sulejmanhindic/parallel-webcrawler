package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import com.udacity.webcrawler.profiler.ProfilingState;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object delegate;
  private final ProfilingState state;

  // TODO: You will need to add more instance fields and constructor arguments to this class.
  ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.delegate = delegate;
    this.state = state;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

    Object invoked = new Object();
    Instant startTime = null;
    Instant endTime = null;
    Duration duration = null;

    if (method.getAnnotation(Profiled.class) != null) {
      try {
        startTime = clock.instant();
        invoked = method.invoke(delegate, args);
        endTime = clock.instant();
        duration = Duration.between(startTime, endTime);
        state.record(delegate.getClass(), method, duration);
      } catch (InvocationTargetException e) {
        endTime = clock.instant();
        duration = Duration.between(startTime, endTime);
        state.record(delegate.getClass(), method, duration);
        throw e.getTargetException();
      } catch (IllegalAccessException e) {
        throw e.getCause();
      }
    } else {
      try {
        if (method.getName().equals("equals")) {
          if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
            invoked = delegate.equals(args[0]);
          } else {
            invoked = method.invoke(delegate, args);
          }
        } else {
          invoked = method.invoke(delegate, args);
        }

      } catch (InvocationTargetException e) {
        throw e.getTargetException();
      } catch (IllegalAccessException e) {
        throw e.getCause();
      }
    }

    return invoked;
  }
  /*
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    System.out.println(getClass());
    System.out.println(o.getClass());
    //if (o == null || getClass() != o.getClass()) return false;
    ProfilingMethodInterceptor that = (ProfilingMethodInterceptor) o;

    return clock.equals(that.clock) && delegate.equals(that.delegate) && state.equals(that.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clock, delegate, state);
  }*/
}
