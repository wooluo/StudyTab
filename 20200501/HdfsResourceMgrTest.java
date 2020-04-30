package io.transwarp.guardian.resource.hdfs;

import io.transwarp.guardian.client.impl.rest.v2.GuardianClientV2RestImpl;
import io.transwarp.guardian.client.v2.GuardianClientV2;
import io.transwarp.guardian.common.conf.GuardianConfiguration;
import io.transwarp.guardian.common.conf.GuardianConstants;
import io.transwarp.guardian.common.conf.GuardianVars;
import io.transwarp.guardian.common.exception.GuardianException;
import io.transwarp.guardian.common.model.v2.*;
import io.transwarp.guardian.resource.ResourceLookupContext;
import io.transwarp.guardian.resource.ResourceServiceManager;
import io.transwarp.guardian.resource.v2.ResourceEntry;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.transwarp.guardian.common.constants.DataSourceConstants.V2Constants.DIR;

@RunWith(SpringRunner.class)
@ContextConfiguration
public class HdfsResourceMgrTest {
  private static final String PREFIX = RandomStringUtils.randomAlphabetic(5) + "HdfsResourceMgrTest";

  private ResourceLookupContext lookupContext = new ResourceLookupContext();
  private Map<String, String> conf = new HashMap<>();
  private HdfsResourceMgr hdfsResourceMgr = new HdfsResourceMgr(conf);
  private SessionVo sessionVo;
  private GuardianClientV2 guardianClient;
  private ResourceServiceManager resourceServiceManager;

  private static final NodeVo DIR_USR = new NodeVo(DIR, "USR");
  private ResourceVo hdfs = new ResourceVo.Builder().serviceType("HDFS").serviceName(PREFIX + "hdfs1").build();
  private ResourceVo hdfsGlobal = ResourceVo.global(hdfs.getServiceType(), hdfs.getServiceName());
  private ResourceVo hdfsRoot = hdfs.service().addNode(DIR, "/").build();
  private ResourceVo hdfsPath = hdfsRoot.asParent().addNode(DIR_USR).build();

  public HdfsResourceMgrTest() {

  }

  @Autowired
  HdfsResourceMgrTest(ResourceServiceManager resourceServiceManager) {
    this.resourceServiceManager = resourceServiceManager;
  }

  @Before
  public void setUp() throws Exception {
    //sessionVo = userManager.login("admin", "123");
    sessionVo = new SessionVo(new UserVo("admin", "123"));
    resourceServiceManager.register(sessionVo, hdfs.getServiceType(), hdfs.getServiceName(), "HdfsResourceMgrTest service", conf);
    GuardianConfiguration configuration = new GuardianConfiguration();
    configuration.setBoolean(GuardianVars.GUARDIAN_CLIENT_CACHE_ENABLED.varname, false);
    guardianClient = new GuardianClientV2RestImpl(configuration);
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void getHdfsResourcesTest() throws GuardianException {
    guardianClient.addPerm(new PermVo(hdfsGlobal, GuardianConstants.ADMIN_PERM_ACTION));
    guardianClient.addPerm(new PermVo(hdfsPath, GuardianConstants.ADMIN_PERM_ACTION));

    lookupContext.setSessionVo(sessionVo);
    lookupContext.setUser("admin");
    lookupContext.setResourceType("path");
    ResourceEntry entry = new ResourceEntry(new NodeVo("dir", "/"));
    Assert.assertEquals(Collections.singletonList(entry), hdfsResourceMgr.getHdfsResources(lookupContext));

    lookupContext.setUser("hdfs");
    lookupContext.setResourceType("path");
    lookupContext.setUserInputUri("USR");
    Assert.assertEquals(Collections.emptyList(), hdfsResourceMgr.getHdfsResources(lookupContext));

    lookupContext.setUser("admin");
    lookupContext.setResourceType(null);
    lookupContext.setParentDataSource(Collections.singletonList(new NodeVo("dir", "/")));
    Assert.assertEquals(Collections.emptyList(), hdfsResourceMgr.getHdfsResources(lookupContext));

    lookupContext.setUser("hdfs");
    lookupContext.setParentDataSource(Collections.singletonList(DIR_USR));
    lookupContext.setResourceType("director");
    Assert.assertEquals(Collections.emptyList(), hdfsResourceMgr.getHdfsResources(lookupContext));
  }
}