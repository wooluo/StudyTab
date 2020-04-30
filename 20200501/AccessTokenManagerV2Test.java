package io.transwarp.guardian.core.manager.v2;

import io.transwarp.guardian.common.conf.GuardianConfiguration;
import io.transwarp.guardian.common.conf.GuardianConstants;
import io.transwarp.guardian.common.conf.GuardianVars;
import io.transwarp.guardian.common.exception.GuardianClientException;
import io.transwarp.guardian.common.exception.GuardianException;
import io.transwarp.guardian.common.model.AccessTokenStatus;
import io.transwarp.guardian.common.model.AccessTokenVo;
import io.transwarp.guardian.common.model.v2.SessionVo;
import io.transwarp.guardian.common.model.v2.UserAdminRoleVo;
import io.transwarp.guardian.common.model.v2.UserVo;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.List;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestConfiguration.class)
public class AccessTokenManagerV2Test {

  private static GuardianConfiguration conf = new GuardianConfiguration();
  private static final Logger LOG = LoggerFactory.getLogger(AccessTokenManagerV2Test.class);
  private static final String PREFIX = RandomStringUtils.randomAlphabetic(5) + "AccessTokenManagerV2Test-";
  private UserVo u1;
  private UserVo u2;
  private SessionVo sessionVo1;
  private SessionVo sessionVo2;
  private AccessTokenVo tokenVo1;
  private AccessTokenVo tokenVo2;


  @Autowired
  private AccessTokenManager accessTokenManager;

  @Autowired
  private UserManager userManager;

  @Autowired
  private AdminManager adminManager;

  @Before
  public void setUp() throws Exception {
    u1 = new UserVo(PREFIX + "u1");
    u1.setUserPassword("123456");
    userManager.addUser(null, u1);
    u2 = new UserVo(PREFIX + "u2");
    u2.setUserPassword("123456");
    userManager.addUser(null, u2);
    sessionVo1 = userManager.login(u1.getUsername(), "123456");
    sessionVo2 = userManager.login(u2.getUsername(), "123456");
    tokenVo1 = new AccessTokenVo("att-token-1");
    tokenVo2 = new AccessTokenVo("att-token-2");
    conf.setBoolean(GuardianVars.GUARDIAN_SERVER_ACCESS_TOKEN_ADMIN_PERM_ENABLED.varname, true);
  }

  @After
  public void tearDown() throws Exception {
    userManager.deleteUser(null, PREFIX + "u1");
    userManager.deleteUser(null, PREFIX + "u2");
    if (tokenVo1.getId() != null) {
      accessTokenManager.deleteAccessToken(null, tokenVo1.getId());
    }
    if (tokenVo2.getId() != null) {
      accessTokenManager.deleteAccessToken(null, tokenVo2.getId());
    }
  }

  @Test
  public void createAccessTokenTest() throws GuardianException {
    LOG.info("========== test create token with null owner ==========");
    AccessTokenVo resultVo1 = accessTokenManager.createAccessToken(sessionVo1, tokenVo1);
    Assert.assertEquals(tokenVo1.getName(), resultVo1.getName());
    Assert.assertEquals(u1.getUsername(), resultVo1.getOwner());
    Assert.assertEquals(AccessTokenStatus.ACTIVE, resultVo1.getStatus());

    LOG.info("========== test create token with wrong owner ==========");
    tokenVo2.setOwner(u2.getUsername());
    try {
      accessTokenManager.createAccessToken(sessionVo1, tokenVo2);
      Assert.fail("FAIL: User " + u1.getUsername() + " cannot create access token for user " + u2.getUsername());
    } catch (GuardianClientException e) {
      LOG.info("SUCCESS:  User " + u1.getUsername() + " shouldn't create access token for user " + u2.getUsername());
    }

    LOG.info("========== test create token with user-admin role ==========");
    UserAdminRoleVo userAdminRoleVo = new UserAdminRoleVo(u1.getUsername(), GuardianConstants.USER_ADMIN_ROLE);
    adminManager.assign(null, userAdminRoleVo);
    AccessTokenVo resultVo2 = accessTokenManager.createAccessToken(sessionVo1, tokenVo2);
    Assert.assertEquals(tokenVo2.getName(), resultVo2.getName());
    Assert.assertEquals(u2.getUsername(), resultVo2.getOwner());
    Assert.assertEquals(AccessTokenStatus.ACTIVE, resultVo2.getStatus());
    adminManager.deassign(sessionVo1, userAdminRoleVo);
  }

  @Test
  public void getAccessTokenByIdTest() throws GuardianException {
    LOG.info("========== test get token with Id ==========");
    accessTokenManager.createAccessToken(sessionVo1, tokenVo1);
    AccessTokenVo resultVo1 = accessTokenManager.getAccessTokenById(sessionVo1, tokenVo1.getId());
    Assert.assertEquals(tokenVo1.getName(), resultVo1.getName());
    Assert.assertEquals(u1.getUsername(), resultVo1.getOwner());
    Assert.assertEquals(tokenVo1.getId(), resultVo1.getId());

    LOG.info("========== test get token with wrong owner ==========");
    tokenVo2.setOwner(u2.getUsername());
    accessTokenManager.createAccessToken(sessionVo2, tokenVo2);
    try {
      AccessTokenVo resultVo233 =accessTokenManager.getAccessTokenById(sessionVo1, tokenVo2.getId());
      LOG.info("ttttt"+ resultVo233.getId());
      Assert.fail("FAIL: User " + u1.getUsername() + " cannot get access token for user " + u2.getUsername());
    } catch (GuardianClientException e) {
      LOG.info("SUCCESS:  User " + u1.getUsername() + " shouldn't get access token for user " + u2.getUsername());
    }

    LOG.info("========== test get token with user-admin role ==========");
    UserAdminRoleVo userAdminRoleVo = new UserAdminRoleVo(u1.getUsername(), GuardianConstants.USER_ADMIN_ROLE);
    adminManager.assign(null, userAdminRoleVo);
    AccessTokenVo resultVo2 = accessTokenManager.getAccessTokenById(sessionVo1, tokenVo2.getId());
    Assert.assertEquals(tokenVo2.getName(), resultVo2.getName());
    Assert.assertEquals(u2.getUsername(), resultVo2.getOwner());
    Assert.assertEquals(tokenVo2.getId(), resultVo2.getId());
    adminManager.deassign(sessionVo1, userAdminRoleVo);

  }

  @Test
  public void getAccessTokenByOwnerTest() throws GuardianException {
    LOG.info("========== test get token with null owner ==========");
    tokenVo1.setOwner(u1.getUsername());
    accessTokenManager.createAccessToken(sessionVo1, tokenVo1);
    List<AccessTokenVo> resultVos1 = accessTokenManager.getAccessTokenByOwner(sessionVo1, u1.getUsername(), false);
    Assert.assertEquals(1, resultVos1.size());
    Assert.assertEquals(tokenVo1.getId(), resultVos1.get(0).getId());
    Assert.assertEquals(tokenVo1.getName(), resultVos1.get(0).getName());

    LOG.info("========== test get token with wrong owner ==========");
    tokenVo2.setOwner(u2.getUsername());
    accessTokenManager.createAccessToken(null, tokenVo2);
    tokenVo2.setContent(null);
    try {
      accessTokenManager.getAccessTokenByOwner(sessionVo1, u2.getUsername(), false);
      Assert.fail("FAIL: User " + u1.getUsername() + " cannot get access token for user " + u2.getUsername());
    } catch (GuardianClientException e) {
      LOG.info("SUCCESS:  User " + u1.getUsername() + " shouldn't get access token for user " + u2.getUsername());
    }

    LOG.info("========== test find tokens/get token with oener with user-admin role ==========");
    UserAdminRoleVo userAdminRoleVo = new UserAdminRoleVo(u1.getUsername(), GuardianConstants.USER_ADMIN_ROLE);
    adminManager.assign(null, userAdminRoleVo);
    List<AccessTokenVo> resultVos2 = accessTokenManager.getAccessTokenByOwner(sessionVo1, null, false);
    Assert.assertEquals(2, resultVos2.size());
    Assert.assertEquals(tokenVo1.getId(), resultVos1.get(0).getId());
    Assert.assertEquals(tokenVo2.getId(), resultVos1.get(1).getId());
    List<AccessTokenVo> resultVos3 = accessTokenManager.getAccessTokenByOwner(sessionVo1, u2.getUsername(), false);
    Assert.assertEquals(1, resultVos3.size());
    Assert.assertEquals(tokenVo2.getId(), resultVos1.get(0).getId());
    adminManager.deassign(null, userAdminRoleVo);
  }

  @Test
  public void updateAccessTokenByIdTest() throws GuardianException {
    LOG.info("========== test update token ==========");
    accessTokenManager.createAccessToken(sessionVo1, tokenVo1);
    AccessTokenVo updateVo1 = new AccessTokenVo("update-token-1");
    updateVo1.setId(tokenVo1.getId());
    updateVo1.setName("update-token");
    AccessTokenVo resultVo1 = accessTokenManager.updateAccessTokenById(sessionVo1, updateVo1);
    Assert.assertEquals(updateVo1.getName(), resultVo1.getName());
    Assert.assertEquals(u1.getUsername(), resultVo1.getOwner());
    Assert.assertEquals(updateVo1.getId(), resultVo1.getId());

  }

  @Test
  public void deleteAccessTokenTest() throws GuardianException {
    LOG.info("========== test delete token ==========");
    UserAdminRoleVo userAdminRoleVo = new UserAdminRoleVo(u1.getUsername(), GuardianConstants.USER_ADMIN_ROLE);
    adminManager.assign(null, userAdminRoleVo);
    tokenVo2.setOwner(u2.getUsername());
    accessTokenManager.createAccessToken(sessionVo1, tokenVo1);
    accessTokenManager.createAccessToken(sessionVo1, tokenVo2);
    accessTokenManager.deleteAccessToken(sessionVo1, tokenVo1.getId());
    accessTokenManager.deleteAccessToken(sessionVo1, tokenVo2.getId());
    List<AccessTokenVo> resultVos = accessTokenManager.getAccessTokenByOwner(sessionVo1, null, true);
    Assert.assertEquals(Collections.emptyList(), resultVos);
    adminManager.deassign(null, userAdminRoleVo);
  }

}