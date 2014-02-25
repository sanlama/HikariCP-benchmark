/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.zaxxer.hikari.benchmark;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class StatementBench
{
    private static DataSource hikariDS;
    private static DataSource tomcatDS;
    private static DataSource boneDS;

    @Setup
    public void setup()
    {
        HikariConfig config = new HikariConfig();
        config.setAcquireIncrement(5);
        config.setConnectionTimeout(8000);
        config.setIdleTimeout(TimeUnit.MINUTES.toMillis(30));
        config.setJdbc4ConnectionTest(true);
        config.setDataSourceClassName("com.zaxxer.hikari.benchmark.StubDataSource");
        config.setUseInstrumentation(true);

        hikariDS = new HikariDataSource(config);

        try
        {
            Class.forName("com.zaxxer.hikari.benchmark.StubDriver");
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        BoneCPConfig bconfig = new BoneCPConfig();
        bconfig.setAcquireIncrement(5);
        bconfig.setMinConnectionsPerPartition(10);
        bconfig.setMaxConnectionsPerPartition(60);
        bconfig.setConnectionTimeoutInMs(8000);
        bconfig.setIdleMaxAgeInMinutes(30);
        bconfig.setConnectionTestStatement("VALUES 1");
        bconfig.setCloseOpenStatements(true);
        bconfig.setDisableConnectionTracking(true);
        bconfig.setJdbcUrl("jdbc:stub");
        bconfig.setUsername("nobody");
        bconfig.setPassword("nopass");

        boneDS = new BoneCPDataSource(bconfig);

        PoolProperties p = new PoolProperties();
        p.setUrl("jdbc:stub");
        p.setDriverClassName("com.zaxxer.hikari.benchmark.StubDriver");
        p.setUsername("sa");
        p.setPassword("");
        p.setInitialSize(10);
        p.setMinIdle(10);
        p.setMaxIdle(60);
        p.setMaxActive(60);
        p.setMaxWait(8000);
        p.setMinEvictableIdleTimeMillis((int) TimeUnit.MINUTES.toMillis(30));
        p.setTestOnBorrow(true);
        p.setValidationQuery("VALUES 1");
        p.setJdbcInterceptors("StatementFinalizer");

        tomcatDS = new org.apache.tomcat.jdbc.pool.DataSource(p);
    }

    @GenerateMicroBenchmark
    public PreparedStatement testHikari(HikariState state)
    {
        return test(state.connection);
    }

    @GenerateMicroBenchmark
    public PreparedStatement testBone(BoneState state)
    {
        return test(state.connection);
    }

    @GenerateMicroBenchmark
    public PreparedStatement testTomcat(TomcatState state)
    {
        return test(state.connection);
    }

    private PreparedStatement test(Connection connection)
    {
        try
        {
            PreparedStatement prepareStatement = connection.prepareStatement("INSERT INTO test (column) VALUES (?)");
            prepareStatement.close();
            return prepareStatement;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    @State(Scope.Thread)
    public static class HikariState
    {
        Connection connection;

        @Setup(Level.Iteration)
        public void setup() throws SQLException
        {
            connection = hikariDS.getConnection();
        }

        @TearDown(Level.Iteration)
        public void teardown() throws SQLException
        {
            connection.close();
        }
    }

    @State(Scope.Thread)
    public static class BoneState
    {
        Connection connection;

        @Setup(Level.Iteration)
        public void setup() throws SQLException
        {
            connection = boneDS.getConnection();
        }

        @TearDown(Level.Iteration)
        public void teardown() throws SQLException
        {
            connection.close();
        }
    }

    @State(Scope.Thread)
    public static class TomcatState
    {
        Connection connection;

        @Setup(Level.Iteration)
        public void setup() throws SQLException
        {
            connection = tomcatDS.getConnection();
        }

        @TearDown(Level.Iteration)
        public void teardown() throws SQLException
        {
            connection.close();
        }
    }
}
