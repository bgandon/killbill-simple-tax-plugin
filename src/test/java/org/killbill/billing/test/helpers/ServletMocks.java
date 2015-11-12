/*
 * Copyright 2015 Benjamin Gandon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.killbill.billing.test.helpers;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.base.Charsets;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;

/**
 * A simple Mockito-based mocking context that is useful when testing a
 * request/response on an HTTP Servlet.
 * <p>
 * This is clearly inspired by
 * {@code org.springframework.mock.web.MockHttpServletRequest} and
 * {@code org.springframework.mock.web.MockHttpServletResponse}, but our
 * approach here is to implement the interfaces using Mockito. The other major
 * difference is that we don't have any overkill dependency on the Spring
 * Framework.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class ServletMocks {

    public static final int SC_UNKNOWN = 0;

    private HttpServletRequest req;
    private HttpServletResponse resp;

    private int status = SC_UNKNOWN;
    private String contentType = null;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ListMultimap<String, String> headers = ArrayListMultimap.create();

    public HttpServletRequest req() {
        return req;
    }

    public HttpServletResponse resp() {
        return resp;
    }

    public int getResponseStatus() {
        return status;
    }

    public String getResponseContentType() {
        return contentType;
    }

    public ListMultimap<String, String> getHeaders() {
        return headers;
    }

    public String getResponseContent() {
        return new String(out.toByteArray(), Charsets.UTF_8);
    }

    public ServletMocks() {
        super();
        req = mock(HttpServletRequest.class);
        try {
            resp = mockResp();
        } catch (IOException exc) {
            new RuntimeException(exc);
        }
    }

    public void withRequestBody(String requestBody) throws IOException {
        final ByteArrayInputStream in = new ByteArrayInputStream(requestBody.getBytes());

        when(req.getInputStream()).then(new Answer<ServletInputStream>() {
            @Override
            public ServletInputStream answer(InvocationOnMock invocation) throws Throwable {
                return new ServletInputStream() {

                    @Override
                    public int read() throws IOException {
                        return in.read();
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                        try {
                            readListener.onDataAvailable();
                            readListener.onAllDataRead();
                        } catch (IOException letItCrash) {
                            throw new RuntimeException(letItCrash);
                        }
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public boolean isFinished() {
                        return in.available() > 0;
                    }
                };
            }
        });
    }

    private HttpServletResponse mockResp() throws IOException {
        HttpServletResponse resp = mock(HttpServletResponse.class);

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                status = invocation.getArgumentAt(0, Integer.class);
                return null;
            }
        }).when(resp).sendError(anyInt(), anyString());

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                status = invocation.getArgumentAt(0, Integer.class);
                return null;
            }
        }).when(resp).setStatus(Matchers.anyInt());

        when(resp.getStatus()).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                return status;
            }
        });

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                contentType = invocation.getArgumentAt(0, String.class);
                return null;
            }
        }).when(resp).setContentType(anyString());

        when(resp.getContentType()).then(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                return contentType;
            }
        });

        when(resp.getOutputStream()).thenAnswer(new Answer<ServletOutputStream>() {
            @Override
            public ServletOutputStream answer(InvocationOnMock invocation) throws Throwable {
                return new ServletOutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        out.write(b);
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        try {
                            writeListener.onWritePossible();
                        } catch (IOException letItCrash) {
                            throw new RuntimeException(letItCrash);
                        }
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }
                };
            }
        });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                headers.put(invocation.getArgumentAt(0, String.class), invocation.getArgumentAt(1, String.class));
                return null;
            }
        }).when(resp).addHeader(anyString(), anyString());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String name = invocation.getArgumentAt(0, String.class);
                headers.removeAll(name);
                headers.put(name, invocation.getArgumentAt(1, String.class));
                return null;
            }
        }).when(resp).setHeader(anyString(), anyString());

        when(resp.containsHeader(anyString())).then(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                return headers.containsKey(invocation.getArgumentAt(0, String.class));
            }
        });
        when(resp.getHeader(anyString())).then(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                List<String> values = headers.get(invocation.getArgumentAt(0, String.class));
                if (values.isEmpty()) {
                    return null;
                }
                return values.get(0);
            }
        });
        when(resp.getHeaders(anyString())).then(new Answer<Collection<String>>() {
            @Override
            public Collection<String> answer(InvocationOnMock invocation) throws Throwable {
                return headers.get(invocation.getArgumentAt(0, String.class));
            }
        });
        when(resp.getHeaderNames()).then(new Answer<Collection<String>>() {
            @Override
            public Collection<String> answer(InvocationOnMock invocation) throws Throwable {
                return ImmutableSet.copyOf(headers.keySet());
            }
        });

        return resp;
    }

}
