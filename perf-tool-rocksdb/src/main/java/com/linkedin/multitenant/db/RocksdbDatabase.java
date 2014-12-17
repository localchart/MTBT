/**
 * Copyright 2014 LinkedIn Corp. Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.linkedin.multitenant.db;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import com.linkedin.multitenant.common.Query;
import com.linkedin.multitenant.main.RunExperiment;

public class RocksdbDatabase implements Database
{
  private class MyResponseHandler implements ResponseHandler<String>
  {
    public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException
    {
      int status = response.getStatusLine().getStatusCode();
      if(status >= 200 && status < 300)
      {
        HttpEntity ent = response.getEntity();
        if(ent == null)
          return null;
        else
          return EntityUtils.toString(ent);
      }
      else
      {
        throw new ClientProtocolException("Something wrong");
      }
    }
  }

  private static final Logger m_log = Logger.getLogger(RocksdbDatabase.class);

  public static final String FLAG_ROCKSDB_DB_NAME    = "rocksdb.dbName";

  protected CloseableHttpClient m_client;
  protected String m_hostName;
  protected int m_hostPort;
  protected String m_dbName;
  protected MyResponseHandler m_handler;
  protected String m_connStr;

  public RocksdbDatabase()
  {
    RequestConfig rq = RequestConfig.custom().setStaleConnectionCheckEnabled(false).build();

    m_client = HttpClients.custom()
                          .setDefaultRequestConfig(rq)
                          .setMaxConnTotal(2)
                          .build();
    m_handler = new MyResponseHandler();
  }

  @Override
  public DatabaseResult init(Map<String, String> workPlanProperties, Map<String, String> jobProperties) throws Exception
  {
    String hostName;
    int hostPort;
    String dbName;
    String jobName;

    //get job name
    String temp = getParamStr(jobProperties, RunExperiment.FLAG_JOB_NAME);
    if(temp == null)
    {
      m_log.fatal("Job name is not specified");
      return DatabaseResult.FAIL;
    }
    else
    {
      jobName = temp;
    }

    //get hostname
    temp = getParamStr(workPlanProperties, RunExperiment.FLAG_WORK_HOST);
    if(temp == null)
    {
      m_log.fatal("Hostname is not specified");
      return DatabaseResult.FAIL;
    }
    else
    {
      hostName = temp;
    }

    //get host port
    hostPort = getParamInt(workPlanProperties, RunExperiment.FLAG_WORK_PORT);
    if(hostPort == -1)
    {
      m_log.fatal("Host port is not specified");
      return DatabaseResult.FAIL;
    }

    //get db name
    temp = getParamStr(jobProperties, FLAG_ROCKSDB_DB_NAME);
    if(temp == null)
    {
      m_log.fatal("Rocksdb database name is not specified for job " + jobName);
      return DatabaseResult.FAIL;
    }
    else
    {
      dbName = temp;
    }

    return init(hostName, hostPort, dbName);
  }

  public DatabaseResult init(String hostName, int hostPort, String dbName)
  {
    m_hostName = hostName;
    m_hostPort = hostPort;
    m_dbName = dbName;

    m_connStr = "http://" + m_hostName + ":" + m_hostPort + "/" + m_dbName + "/";

    return DatabaseResult.OK;
  }

  @Override
  public DatabaseResult doInsert(Query q)
  {
    HttpPut put = new HttpPut(m_connStr + q.getKey());

    ByteArrayEntity bae = new ByteArrayEntity(q.getValue());
    bae.setContentType("octet-stream");
    put.setEntity(bae);

    try
    {
      String responseBody = m_client.execute(put, m_handler);
      m_log.debug("Write response: " + responseBody);
      return DatabaseResult.OK;
    }
    catch (Exception e)
    {
      m_log.error("Error in executing doInsert", e);
      return DatabaseResult.FAIL;
    }
  }

  @Override
  public DatabaseResult doUpdate(Query q)
  {
    HttpPut put = new HttpPut(m_connStr + q.getKey());

    ByteArrayEntity bae = new ByteArrayEntity(q.getValue());
    bae.setContentType("octet-stream");
    put.setEntity(bae);

    try
    {
      String responseBody = m_client.execute(put, m_handler);
      m_log.debug("Write response: " + responseBody);
      return DatabaseResult.OK;
    }
    catch (Exception e)
    {
      m_log.error("Error in executing doUpdate", e);
      return DatabaseResult.FAIL;
    }
  }

  @Override
  public DatabaseResult doRead(Query q)
  {
    HttpGet get = new HttpGet(m_connStr + q.getKey());

    try
    {
      String responseBody = m_client.execute(get, m_handler);
      m_log.debug("Read response: " + responseBody);
      return DatabaseResult.OK;
    }
    catch (Exception e)
    {
      m_log.error("Error in executing doRead", e);
      return DatabaseResult.FAIL;
    }
  }

  @Override
  public DatabaseResult doDelete(Query q)
  {
    HttpDelete delete = new HttpDelete(m_connStr + q.getKey());

    try
    {
      String responseBody = m_client.execute(delete, m_handler);
      m_log.debug("Delete response: " + responseBody);

      return DatabaseResult.OK;
    }
    catch (Exception e)
    {
      m_log.error("Error in executing doDelete", e);
      return DatabaseResult.FAIL;
    }
  }

  @Override
  public DatabaseResult close()
  {
    try
    {
      m_client.close();
      return DatabaseResult.OK;
    }
    catch(Exception e)
    {
      return DatabaseResult.FAIL;
    }
  }

  private String getParamStr(Map<String, String> properties, String propertyName)
  {
    String val = properties.get(propertyName);
    if(val == null)
      return null;
    else
      return val;
  }

  private int getParamInt(Map<String, String> properties, String propertyName)
  {
    String val = properties.get(propertyName);
    if(val == null)
      return -1;
    else
      return Integer.parseInt(val);
  }
}
