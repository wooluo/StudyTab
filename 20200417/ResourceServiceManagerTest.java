package io.transwarp.guardian.resource;

import io.transwarp.guardian.client.v2.GuardianClientV2;
import io.transwarp.guardian.client.v2.GuardianClientV2Factory;
import io.transwarp.guardian.common.conf.GuardianConfiguration;
import io.transwarp.guardian.common.conf.GuardianConstants;
import io.transwarp.guardian.common.exception.GuardianClientException;
import io.transwarp.guardian.common.exception.GuardianException;
import io.transwarp.guardian.common.model.QuotaVo;
import io.transwarp.guardian.common.model.ServiceVo;
import io.transwarp.guardian.common.model.UserVo;
import io.transwarp.guardian.common.model.v2.ResourceVo;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.*;

import static org.junit.Assert.*;

@Ignore
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ResourceServiceManagerTest {

  String HDFS = "HDFS";
  String HDFS_SERVICE_ID = "hdfs.service.id";
  String YARN = "YARN";
  String YARN_SERVICE_ID = "yarn.service.id";
  String HYPERBASE = "HYPERBASE";
  String HYPERBASE_SERVICE_ID = "hbase.service.id";

  ServiceVo hdfsService = new ServiceVo();
  ServiceVo yarnService = new ServiceVo();
  ServiceVo hbaseService = new ServiceVo();
  ServiceVo inceptorService = new ServiceVo();

  @Test
  public void test1_registerService() throws GuardianClientException {
    System.out.println("Register services......");
    GuardianClientV2 clientV2 = GuardianClientV2Factory.getInstance();

    Map<String, String> hdfsConfigs = new HashMap<>();
    hdfsConfigs.put(GuardianConstants.SERVICE_STARTUP_USER, "hdfs");
    hdfsConfigs.put(GuardianConstants.SERVICE_REGISTER_HOST, "tw-node1194");
    hdfsConfigs.put(HDFS_SERVICE_ID, "hdfs1");
    hdfsService.setServiceType(HDFS);
    hdfsService.setServiceName("hdfs1");
    hdfsService.setDescription("hdfs service");
    hdfsService.setConfigs(hdfsConfigs);
    clientV2.registerService(new ServiceVo(HDFS, "hdfs1"));


    Map<String, String> yarnConfigs  = new HashMap<>();
    yarnConfigs.put(GuardianConstants.SERVICE_STARTUP_USER, "yarn");
    yarnConfigs.put(GuardianConstants.SERVICE_REGISTER_HOST, "tw-node1194");
    yarnConfigs.put(YARN_SERVICE_ID, "yarn1");
    yarnConfigs.put("yarn.resourcemanager.scheduler.class", "org.apache.hadoop.yarn.server.resourcemanager.scheduler.capacity.CapacityScheduler");
    yarnConfigs.put("yarn.resourcemanager.webapp.address", "tw-node1194:8088");
    yarnService.setServiceType(YARN);
    yarnService.setServiceName("yarn1");
    yarnService.setDescription("yarn service");
    yarnService.setConfigs(yarnConfigs);
    clientV2.registerService(new ServiceVo(YARN, "yarn1"));

    Map<String, String> hbaseConfigs = new HashMap<>();
    hbaseConfigs.put(GuardianConstants.SERVICE_STARTUP_USER, "hbase");
    hbaseConfigs.put(GuardianConstants.SERVICE_REGISTER_HOST, "tw-node1194");
    hbaseConfigs.put(HYPERBASE_SERVICE_ID, "hyperbase1");
    hbaseService.setServiceType(HYPERBASE);
    hbaseService.setServiceName("hyperbase1");
    hbaseService.setDescription("hyperbase service");
    hbaseService.setConfigs(hbaseConfigs);
    clientV2.registerService(new ServiceVo(HYPERBASE, "hyperbase1"));
    System.out.println("HYPERBASE service hyperbase1 registered.");

  }
  @Test
  public void test2_addQuota() throws GuardianClientException {
    System.out.println("Testing quota operations......");
    GuardianClientV2 clientV2 = GuardianClientV2Factory.getInstance();
    QuotaVo quota = new QuotaVo();
    QuotaVo quota1 = new QuotaVo("quotaTest1", Arrays.asList("FURION_SCHEDULER", "root"));
    QuotaVo readQuota = new QuotaVo();
    QuotaVo readQuotaHdfs = new QuotaVo();

    //hdfs
    quota.setComponent("hdfs1");
    quota.setDataSource(Arrays.asList(new String[]{"PATH", "/"}));
    readQuota.setComponent("hdfs1");
    readQuota.setDataSource(Arrays.asList(new String[]{"PATH", "/"}));

    System.out.println("Adding hdfs quota......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 10000));
    clientV2.addQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());

    System.out.println("Updating hdfs quota......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 20000));
    admin.updateQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());

    //inceptor
    //database
    quota.setComponent("inceptor1");
    quota.setDataSource(Arrays.asList(new String[]{"DATABASE", "test_db"}));
    readQuota.setComponent("inceptor1");
    readQuota.setDataSource(Arrays.asList(new String[]{"DATABASE", "test_db"}));
    readQuotaHdfs.setComponent("hdfs1");
    readQuotaHdfs.setDataSource(Arrays.asList(new String[]{"PATH", "/", "inceptorsql1", "user", "hive", "warehouse", "test_db.db"}));

    System.out.println("Adding inceptor quota for database......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 1000));
    admin.addQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    System.out.println("Updating inceptor quota for database......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 2000));
    admin.updateQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    //database user
    quota.setDataSource(Arrays.asList(new String[]{"DATABASE", "test_db", "u1"}));
    readQuota.setDataSource(Arrays.asList(new String[]{"DATABASE", "test_db", "u1"}));
    readQuotaHdfs.setDataSource(Arrays.asList(new String[]{"PATH", "/", "inceptorsql1", "user", "hive", "warehouse", "test_db.db", "u1"}));

    System.out.println("Adding inceptor quota for user using database......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 100));
    admin.addQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    System.out.println("Updating inceptor quota for user using database......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 200));
    admin.updateQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    //temporary
    quota.setDataSource(Arrays.asList(new String[]{"TEMPORARY"}));
    readQuota.setDataSource(Arrays.asList(new String[]{"TEMPORARY"}));
    readQuotaHdfs.setDataSource(Arrays.asList(new String[]{"PATH", "/", "inceptorsql1", "tmp", "hive"}));

    System.out.println("Adding inceptor quota for temporary space......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 1000));
    admin.addQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    System.out.println("Updating inceptor quota for temporary space......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 2000));
    admin.updateQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    //temporary user
    quota.setDataSource(Arrays.asList(new String[]{"TEMPORARY", "u1"}));
    readQuota.setDataSource(Arrays.asList(new String[]{"TEMPORARY", "u1"}));
    readQuotaHdfs.setDataSource(Arrays.asList(new String[]{"PATH", "/", "inceptorsql1", "tmp", "hive", "u1"}));

    System.out.println("Adding inceptor quota for user using temporary space......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 100));
    admin.addQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

    System.out.println("Updating inceptor quota for user using temporary space......");
    quota.setProperties(Collections.<String, Object>singletonMap("spaceQuota", 200));
    admin.updateQuota(quota);
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuota).getProperties());
    Assert.assertEquals(quota.getProperties(), client.readQuota(readQuotaHdfs).getProperties());

  }

  @Test
  public void test3_deleteQuota() throws GuardianClientException {
    GuardianClientV2 clientV2 = GuardianClientV2Factory.getInstance();
    QuotaVo quota = new QuotaVo();

    System.out.println("Deleting hdfs quota......");
    quota.setComponent("hdfs1");
    quota.setDataSource(Arrays.asList(new String[]{"PATH", "/"}));
    clientV2.deleteQuota(new ResourceVo(HDFS, "hdfs1", new ArrayList<>()));

  }

  @Test
  public void test4_getYarnInactiveQueues() throws GuardianException {
    GuardianAdmin admin = GuardianAdminFactory.getInstance();
    ResourceServiceManager manager = ResourceServiceManager.getInstance();

    QuotaVo q1 = new QuotaVo("yarn1", Arrays.asList(PluginContants.CAPACITY_SCHEDULER, "root", "q1"));
    YarnCapacityQuotaProps props = new YarnCapacityQuotaProps();
    props.setCapacity(100f);
    props.setMaxAMResource(0.1f);
    props.setMaxApplications(10000);
    props.setMaximumCapacity(-1f);
    props.setState("RUNNING");
    // props.setUserLimit();
    props.setUserLimitFactor(1f);
    q1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(q1);

    QuotaVo q11 = new QuotaVo("yarn1", Arrays.asList(PluginContants.CAPACITY_SCHEDULER, "root", "q1", "q11"));
    props = new YarnCapacityQuotaProps();
    props.setCapacity(100f);
    props.setMaxAMResource(0.1f);
    props.setMaxApplications(10000);
    props.setMaximumCapacity(-1f);
    props.setState("RUNNING");
    // props.setUserLimit();
    props.setUserLimitFactor(1f);
    q11.setProperties(PropUtils.props2Map(props));
    admin.addQuota(q11);

    QuotaVo q12 = new QuotaVo("yarn1", Arrays.asList(PluginContants.CAPACITY_SCHEDULER, "root", "q1", "q12"));
    props = new YarnCapacityQuotaProps();
    props.setCapacity(100f);
    props.setMaxAMResource(0.1f);
    props.setMaxApplications(10000);
    props.setMaximumCapacity(-1f);
    props.setState("RUNNING");
    // props.setUserLimit();
    props.setUserLimitFactor(1f);
    q12.setProperties(PropUtils.props2Map(props));
    admin.addQuota(q12);

    QuotaVo q2 = new QuotaVo("yarn1", Arrays.asList(PluginContants.CAPACITY_SCHEDULER, "root", "default", "q2"));
    props = new YarnCapacityQuotaProps();
    props.setCapacity(100f);
    props.setMaxAMResource(0.1f);
    props.setMaxApplications(10000);
    props.setMaximumCapacity(-1f);
    props.setState("RUNNING");
    // props.setUserLimit();
    props.setUserLimitFactor(1f);
    q2.setProperties(PropUtils.props2Map(props));
    admin.addQuota(q2);

    List<ResourceEntry> queues = manager.getInactiveSchedulerNodes(null, PluginContants.YARN, "yarn1");
    Assert.assertEquals(4, queues.size());
    Assert.assertTrue(queues.contains(new ResourceEntry("root.default.q2", "q2", ResourceEntry.Type.QUEUE)));
    Assert.assertTrue(queues.contains(new ResourceEntry("root.q1", "q1", ResourceEntry.Type.QUEUE)));
    Assert.assertTrue(queues.contains(new ResourceEntry("root.q1.q11", "q11", ResourceEntry.Type.QUEUE)));
    Assert.assertTrue(queues.contains(new ResourceEntry("root.q1.q12", "q12", ResourceEntry.Type.QUEUE)));

  }

  @Test
  public void test5_getInceptorInactivePools() throws GuardianException {
    GuardianAdmin admin = GuardianAdminFactory.getInstance();
    ResourceServiceManager manager = ResourceServiceManager.getInstance();

    QuotaVo furion = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER"));
    admin.addQuota(furion);

    QuotaVo root = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root"));
    SparkFurionSchedulerProps props = new SparkFurionSchedulerProps();
    root.setProperties(PropUtils.props2Map(props));
    admin.addQuota(root);

    QuotaVo adhoc = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "adhoc"));
    adhoc.setProperties(PropUtils.props2Map(props));
    admin.addQuota(adhoc);

    QuotaVo adhoc1 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "adhoc", "adhoc1"));
    adhoc1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(adhoc1);

    QuotaVo adhoc2 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "adhoc", "adhoc2"));
    adhoc2.setProperties(PropUtils.props2Map(props));
    admin.addQuota(adhoc2);

    QuotaVo etl = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "etl"));
    etl.setProperties(PropUtils.props2Map(props));
    admin.addQuota(etl);

    QuotaVo etl1 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "etl", "etl1"));
    etl1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(etl1);

    QuotaVo report = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "report"));
    report.setProperties(PropUtils.props2Map(props));
    admin.addQuota(report);

    QuotaVo report1 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "report", "report1"));
    report1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(report1);

    QuotaVo report2 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "report", "report2"));
    report2.setProperties(PropUtils.props2Map(props));
    admin.addQuota(report2);

    QuotaVo test = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "test"));
    test.setProperties(PropUtils.props2Map(props));
    admin.addQuota(test);

    QuotaVo test1 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "test", "test1"));
    test1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(test1);

    QuotaVo test2 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "test", "test5"));
    test2.setProperties(PropUtils.props2Map(props));
    admin.addQuota(test2);

    QuotaVo p1 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "p1"));
    p1.setProperties(PropUtils.props2Map(props));
    admin.addQuota(p1);

    QuotaVo p11 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "p1", "p11"));
    p11.setProperties(PropUtils.props2Map(props));
    admin.addQuota(p11);

    QuotaVo p2 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "report", "p2"));
    p2.setProperties(PropUtils.props2Map(props));
    admin.addQuota(p2);

    QuotaVo p3 = new QuotaVo("inceptor1", Arrays.asList("FURION_SCHEDULER", "root", "report", "report1", "p3"));
    p3.setProperties(PropUtils.props2Map(props));
    admin.addQuota(p3);

    List<ResourceEntry> pools = manager.getInactiveSchedulerNodes(null, PluginContants.INCEPTOR, "inceptor1");

    Assert.assertEquals(4, pools.size());
    Assert.assertTrue(pools.contains(new ResourceEntry("root.p1", "p1", ResourceEntry.Type.POOL)));
    Assert.assertTrue(pools.contains(new ResourceEntry("root.p1.p11", "p11", ResourceEntry.Type.POOL)));
    Assert.assertTrue(pools.contains(new ResourceEntry("root.report.p2", "p2", ResourceEntry.Type.POOL)));
    Assert.assertTrue(pools.contains(new ResourceEntry("root.report.report1.p3", "p3", ResourceEntry.Type.POOL)));

  }

  @Test
  public void reRegisterService() throws Exception {
    final String SERVICE_TYPE = "TestServiceType";
    final String SERVICE_NAME = "TestServiceName";
    final String SERVICE_DESCRIPTION = "TestServiceDescription";
    UserVo user1 = new UserVo("re-register-service-user1");
    user1.setUserPassword("123");
    UserVo user2 = new UserVo("re-register-service-user2");
    user2.setUserPassword("123");
    GuardianAdmin admin = new RestAdminImpl();
    admin.addUser(user1);
    admin.addUser(user2);
    GuardianConfiguration guardianConf = new GuardianConfiguration();
    guardianConf.set(GuardianVars.GUARDIAN_CONNECTION_USERNAME.varname, user1.getUserName());
    GuardianClient client1 = new RestClientImpl(guardianConf);
    client1.register(SERVICE_TYPE, SERVICE_NAME, SERVICE_DESCRIPTION, null);

    guardianConf.set(GuardianVars.GUARDIAN_CONNECTION_USERNAME.varname, user2.getUserName());
    GuardianClient client2 = new RestClientImpl(guardianConf);
    try {
      client2.register(SERVICE_TYPE, SERVICE_NAME, SERVICE_DESCRIPTION, null);
      Assert.fail("user2 should not be able to register the service already registered by user1");
    } catch (GuardianClientException ex) {

    }
    client1.register(SERVICE_TYPE, SERVICE_NAME, SERVICE_DESCRIPTION, null);

    admin.delUser(user1);
    admin.delUser(user2);
  }
}