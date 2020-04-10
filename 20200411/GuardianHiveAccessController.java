package io.transwarp.guardian.plugins.inceptor;

import com.google.common.collect.Lists;
import io.transwarp.guardian.client.GuardianAdmin;
import io.transwarp.guardian.client.GuardianAdminFactory;
import io.transwarp.guardian.common.conf.GuardianConfiguration;
import io.transwarp.guardian.common.conf.GuardianVars;
import io.transwarp.guardian.common.exception.GuardianClientException;
import io.transwarp.guardian.common.model.*;
import io.transwarp.guardian.common.util.StringUtils;
import io.transwarp.guardian.plugins.common.constants.PluginConstants;
import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.AclEntry;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.ql.ErrorMsg;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.AuthorizationUtils;
import org.apache.hadoop.hive.ql.security.authorization.plugin.*;
import org.apache.hadoop.hive.ql.security.authorization.plugin.sqlstd.SQLAuthorizationUtils;
import org.apache.hadoop.hive.ql.security.authorization.plugin.sqlstd.SQLPrivTypeGrant;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.ql.util.ErrorMsgUtil;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Created by zhaoliu on 16-8-15.
 */
public class GuardianHiveAccessController implements HiveAccessController {
  private static final Logger LOG = LoggerFactory.getLogger(GuardianHiveAccessController.class);

  private static final String ALL = "ALL", DEFAULT = "DEFAULT", NONE = "NONE";
  private final String ADMIN_ONLY_MSG = "User has to belong to ADMIN role and "
          + "have it as current role, for this action.";
  private final String HAS_ADMIN_PRIV_MSG = "grantor need to have ADMIN OPTION on role being"
          + " granted and have it as a current role for this action.";

  private final HiveMetastoreClientFactory metastoreClientFactory;
  private final HiveAuthenticationProvider authenticator;
  private final HiveAuthzSessionContext sessionContext;
  private String component;
  private GuardianAdmin guardianAdmin;
  private GuardianAuthorizer guardianAuthorizer;
  private HiveConf hiveConf;
  private GuardianConfiguration guardianConf;
  private final boolean grantOptionAsAdmin;
  private final boolean filterShowDescs;

  public GuardianHiveAccessController(HiveMetastoreClientFactory metastoreClientFactory,
                                      HiveConf hiveConf, GuardianConfiguration guardianConf, HiveAuthenticationProvider authenticator, HiveAuthzSessionContext ctx)
          throws HiveAuthzPluginException {

    this.metastoreClientFactory = metastoreClientFactory;
    this.authenticator = authenticator;
    this.sessionContext = ctx;
    this.hiveConf = hiveConf;
    this.guardianConf = guardianConf;
    this.filterShowDescs = guardianConf.getBoolean(GuardianVars.GUARDIAN_INCEPTOR_FILTER_SHOW_DESCS.varname,
            GuardianVars.GUARDIAN_INCEPTOR_FILTER_SHOW_DESCS.defaultBoolVal);
    try {
      this.guardianAdmin = GuardianAdminFactory.getInstance(guardianConf);
    } catch (GuardianClientException e) {
      LOG.error("Fail to initialize Guardian Client/Admin when initializing Guardian AccessController", e);
      // TODO: deal with ErrorMsg
      throw new HiveAuthzPluginException(e,
              "Fail to initialize Guardian Client/Admin when initializing Guardian AccessController", ErrorMsg.GENERIC_ERROR);
    }
    // get dependent metastore id
    component = hiveConf.get(PluginConstants.HIVE_METASTORE_SERVICE_ID);
    if (StringUtils.isEmpty(component)) {
      component = hiveConf.get(PluginConstants.HIVE_SERVICE_ID);
      if (StringUtils.isEmpty(component)) {
        component = guardianConf.get(GuardianVars.GUARDIAN_PERMISSION_COMPONENT.varname, GuardianVars.GUARDIAN_PERMISSION_COMPONENT.defaultVal);
        if (StringUtils.isEmpty(component)) {
          LOG.error("Configuration {} or {} must be set when initialize GuardianHiveAccessController", PluginConstants.HIVE_SERVICE_ID, GuardianVars.GUARDIAN_PERMISSION_COMPONENT.varname);
          // TODO: deal with ErrorMsg
          throw new HiveAuthzPluginException("Configuration guardian.permission.component must be set when initialize GuardianHiveAccessController", ErrorMsg.GENERIC_ERROR);
        }
      }
    }
    String grantOptionAsAdminKey = component + "." + GuardianVars.GUARDIAN_GRANT_OPTION_AS_ADMIN.varname;
    grantOptionAsAdmin = guardianConf.getBoolean(grantOptionAsAdminKey, GuardianVars.GUARDIAN_GRANT_OPTION_AS_ADMIN.defaultBoolVal);
    guardianAuthorizer = new GuardianAuthorizer(hiveConf, guardianConf);
  }

  @Override
  public void grantPrivileges(List<HivePrincipal> hivePrincipals, List<HivePrivilege> hivePrivileges, HivePrivilegeObject hivePrivObject, HivePrincipal grantorPrincipal, boolean grantOption) throws HiveAuthzPluginException, HiveAccessControlException {
    List<String> dataSource = InceptorPermUtil.convert(hivePrivObject);

    HivePrivilegeObject.HivePrivilegeObjectType objectType = hivePrivObject.getType();
    if (objectType == null) {
      objectType = HivePrivilegeObject.HivePrivilegeObjectType.GLOBAL;
    }

    hivePrivileges = expandAndValidatePrivileges(hivePrivileges, objectType);

    // authorize the grant
    guardianAuthorizer.authorizeGrant(authenticator.getUserName(), hivePrivileges, hivePrivObject);

    for (HivePrincipal principal : hivePrincipals) {
      for (HivePrivilege privilege : hivePrivileges) {
        PermissionVo permVo = new PermissionVo(component, dataSource, privilege.getName());
        permVo.setGrantable(grantOption);
        if (principal.getType() == null || StringUtils.isEmpty(principal.getType().name())) continue;
        try {
          PrincipalType princType = Enum.valueOf(PrincipalType.class, principal.getType().name());
          EntityPermissionVo entityPermVo = new EntityPermissionVo(principal.getName(), princType, permVo);
          guardianAdmin.grant(entityPermVo);

          // Grant Option as ADMIN permission
          if (grantOptionAsAdmin) {
            if (grantOption && (objectType == HivePrivilegeObject.HivePrivilegeObjectType.GLOBAL
                    || objectType == HivePrivilegeObject.HivePrivilegeObjectType.DATABASE
                    || objectType == HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW)) {
              PermissionVo adminPermVo = new PermissionVo(component, dataSource, "ADMIN");
              EntityPermissionVo entityAdminPermVo = new EntityPermissionVo(principal.getName(), princType, adminPermVo);
              guardianAdmin.grant(entityAdminPermVo);
            }
          }

          // TODO: test Group
        } catch (GuardianClientException e) {
          LOG.error("Fail to grant permission: {} for hiveObj: {} to user: {}", privilege.getName(), dataSource,
                  principal.getName(), e);
          throw new HiveAuthzPluginException(e);
        } catch (IllegalArgumentException e) {
          LOG.warn("Parse hive principal type error", e);
          // ignore
        }
      }
    }
  }

  @Override
  public void revokePrivileges(List<HivePrincipal> hivePrincipals, List<HivePrivilege> hivePrivileges, HivePrivilegeObject hivePrivObject, HivePrincipal grantorPrincipal, boolean grantOption) throws HiveAuthzPluginException, HiveAccessControlException {
    List<String> dataSource = InceptorPermUtil.convert(hivePrivObject);

    HivePrivilegeObject.HivePrivilegeObjectType objectType = hivePrivObject.getType();
    if (objectType == null) {
      objectType = HivePrivilegeObject.HivePrivilegeObjectType.GLOBAL;
    }

    hivePrivileges = expandAndValidatePrivileges(hivePrivileges, objectType);

    // TODO: Record the grantor?
    String userName = authenticator.getUserName();
    boolean isAdmin = guardianAuthorizer.checkPermission(userName, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);
    if (!isAdmin) {
      List<String> deniedMessages = new ArrayList<>();
      deniedMessages.add(userName + " is not ADMIN of " + hivePrivObject);
      SQLAuthorizationUtils.assertNoDeniedPermissions(new HivePrincipal(userName,
              HivePrincipal.HivePrincipalType.USER), HiveOperationType.GRANT_PRIVILEGE, deniedMessages);
    }

    for (HivePrincipal principal : hivePrincipals) {
      for (HivePrivilege privilege : hivePrivileges) {
        PermissionVo permVo = new PermissionVo(component, dataSource, privilege.getName());
        // TODO: deal with grant option
        permVo.setGrantable(grantOption);
        if (principal.getType() == null || StringUtils.isEmpty(principal.getType().name())) continue;
        try {
          PrincipalType princType = Enum.valueOf(PrincipalType.class, principal.getType().name());
          EntityPermissionVo entityPermVo = new EntityPermissionVo(principal.getName(), princType, permVo);
          guardianAdmin.revoke(entityPermVo);
          // TODO: test Group
        } catch (GuardianClientException e) {
          LOG.error("Fail to revoke permission: {} for hiveObj: {} to user: {}", privilege.getName(), dataSource,
                  principal.getName(), e);
          throw new HiveAuthzPluginException(e);
        } catch (IllegalArgumentException e) {
          LOG.warn("Parse hive principal type error", e);
          // ignore
        }
      }
    }
  }

  @Override
  public void createRole(String roleName, HivePrincipal adminGrantor) throws HiveAuthzPluginException, HiveAccessControlException {
    // only user have ADMIN permission in hive can create new roles.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20404, ErrorMsgUtil.toString(userName),
              ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
    }

    RoleVo roleVo = new RoleVo(roleName);
    try {
      LOG.info("Adding role {} to Guardian service", roleName);
      guardianAdmin.addRole(roleVo);
    } catch (GuardianClientException e) {
      LOG.error("Failed to add role {} to Guardian service", roleName, e);
      throw new HiveAuthzPluginException(e);
    }
  }

  @Override
  public void dropRole(String roleName) throws HiveAuthzPluginException, HiveAccessControlException {
    // only user have ADMIN permission in hive can create new roles.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20405, ErrorMsgUtil.toString(userName),
              ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
    }

    RoleVo roleVo = new RoleVo(roleName);
    try {
      LOG.info("Remove role {} from Guardian service", roleName);
      guardianAdmin.delRole(roleVo);
    } catch (GuardianClientException e) {
      LOG.error("Failed to remove role {} from Guardian service", roleName, e);
      throw new HiveAuthzPluginException(e);
    }
  }

  @Override
  public void grantRole(List<HivePrincipal> hivePrincipals, List<String> roles, boolean grantOption, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    // only user have ADMIN permission in hive can create new roles.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20406, ErrorMsgUtil.toString(userName),
              ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
    }

    for (String roleName : roles) {
      for (HivePrincipal princ : hivePrincipals) {
        if (princ.getType() == null || StringUtils.isEmpty(princ.getType().name())) continue;
        try {
          PrincipalType princType = Enum.valueOf(PrincipalType.class, princ.getType().name());
          // TODO: to group, heritable
          EntityRoleVo entityRoleVo = new EntityRoleVo(princ.getName(), princType, roleName, false);
          LOG.info("Assign role: {} to {}: {} in Guardian Service", roleName, princType, princ.getName());
          guardianAdmin.assign(entityRoleVo);
        } catch (GuardianClientException e) {
          LOG.error("Failed to assign role: {} to {}: {} in Guardian Service", roleName, princ.getType(), princ.getName(), e);
          throw new HiveAuthzPluginException(e);
        } catch (IllegalArgumentException e) {
          LOG.warn("Parse hive principal type error", e);
          // ignore
        }
      }
    }
  }

  @Override
  public void revokeRole(List<HivePrincipal> hivePrincipals, List<String> roles, boolean grantOption, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    // only user have ADMIN permission in hive can create new roles.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20407, ErrorMsgUtil.toString(userName),
              ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
    }

    for (String roleName : roles) {
      for (HivePrincipal princ : hivePrincipals) {
        try {
          PrincipalType princType = Enum.valueOf(PrincipalType.class, princ.getType().name());
          // TODO: group / role
          EntityRoleVo entityRoleVo = new EntityRoleVo(princ.getName(), princType, roleName, false);
          LOG.info("Deassign role: {} to {}: {} in Guardian Service", roleName, princType, princ.getName());
          guardianAdmin.deassign(entityRoleVo);
        } catch (GuardianClientException e) {
          LOG.error("Failed to assign role: {} to {}: {} in Guardian Service", roleName, princ.getType(), princ.getName(), e);
          throw new HiveAuthzPluginException(e);
        } catch (IllegalArgumentException e) {
          LOG.warn("Parse hive principal type error", e);
          // ignore
        }
      }
    }
  }

  @Override
  public void grantQuota(List<HivePrincipal> hivePrincipals, String quota, String database, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    // simplified authorization: only user have ADMIN permission in hive can grant quota.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20410);
    }

    if (quota.equalsIgnoreCase("unlimited")) {
      List<QuotaVo> quotaVos = convertToQuotaObjs(hivePrincipals, database);
      for (QuotaVo quotaVo : quotaVos) {
        try {
          guardianAdmin.deleteQuota(quotaVo);
        } catch (GuardianClientException e) {
          LOG.error("Failed to grant quota: {} in Guardian Service", quotaVo);
          throw new HiveAuthzPluginException(e);
        }
      }
    } else {
      List<QuotaVo> quotaVos = convertToQuotaObjs(hivePrincipals, database);
      long quotaNum = GuardianAuthorizer.spaceQuotaToLong(quota);
      Map<String, Object> quotaProps = new HashMap();
      quotaProps.put("spaceQuota", quotaNum);
      for (QuotaVo quotaVo : quotaVos) {
        try {
          quotaVo.setProperties(quotaProps);
          guardianAdmin.addQuota(quotaVo);
        } catch (GuardianClientException e) {
          LOG.error("Failed to grant quota: {} in Guardian Service", quotaVo);
          throw new HiveAuthzPluginException(e);
        }
      }
    }
  }

  @Override
  public void revokeQuota(List<HivePrincipal> hivePrincipals, String quota, String database, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    // Use grant quota unlimited
    throw new HiveAuthzPluginException(ErrorMsg.ERROR_20436);
  }

  @Override
  public List<String> showQuota(HivePrincipal principal, String database) throws HiveAuthzPluginException, HiveAccessControlException {
    // simplified authorization: only user have ADMIN permission in hive can show quota.
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20413);
    }
    List<QuotaVo> quotaVos = convertToQuotaObjs(principal, database);
    return guardianAuthorizer.showQuota(quotaVos);
  }

  @Override
  public void grantFacl(HivePrincipal hivePrincipal, String facl, String tableName, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    if (tableName == null) {
      throw new HiveAuthzPluginException(ErrorMsg.ERROR_20437);
    }
    FsAction fsAction = FsAction.getFsAction(facl);
    if (fsAction == null) {
      throw new HiveAuthzPluginException(ErrorMsg.ERROR_20438, ErrorMsgUtil.toString(facl));
    }
    String aclPrefix;
    switch (hivePrincipal.getType()) {
      case USER:
        aclPrefix = "user";
        break;
      case GROUP:
        aclPrefix = "group";
        break;
      default:
        throw new HiveAuthzPluginException(ErrorMsg.ERROR_20439);
    }

    // Get current database name, db[0]: dbname, db[1]: tablename
    SessionState currentSession = SessionState.get();
    String[] dbTable = AuthorizationUtils.getDatabaseTableName(currentSession, tableName);
    IMetaStoreClient metastoreClient = metastoreClientFactory.getHiveMetastoreClient();

    // authorize grant facl:
    //   ADMIN can grant facl to users or group.
    //   Users with proper privileges can grant facl to himself.
    String username = authenticator.getUserName();
    HivePrivilegeObject hivePrivObject = new HivePrivilegeObject(
        HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW, dbTable[0], dbTable[1]);
    boolean isUserAdmin = guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);

    String target = hivePrincipal.getName();
    if (!isUserAdmin) {
      if ((!target.equals(username)) || hivePrincipal.getType() != HivePrincipal.HivePrincipalType.USER) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20414, username, target);
      }
      if (fsAction.implies(FsAction.READ) || fsAction.implies(FsAction.EXECUTE)) {
        if (!(guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.SELECT_NOGRANT)
            || guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.SELECT_WGRANT))) {
          throw new HiveAccessControlException(ErrorMsg.ERROR_20414, username, target);
        }
      }
      if (fsAction.implies(FsAction.WRITE)) {
        if (!(guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.INSERT_NOGRANT)
            || guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.INSERT_WGRANT))) {
          throw new HiveAccessControlException(ErrorMsg.ERROR_20414, username, target);
        }
      }
    }

    String baseAcl = aclPrefix + ":" + hivePrincipal.getName() + ":" + facl;
    List<AclEntry> aclEntries = Lists.newArrayList();
    aclEntries.add(AclEntry.parseAclEntry(baseAcl, true));
    aclEntries.add(AclEntry.parseAclEntry("default:" + baseAcl, true));
    try {
      Table table = metastoreClient.getTable(dbTable[0], dbTable[1]);
      Path targetPath = new Path(table.getSd().getLocation());
      FileSystem fs = targetPath.getFileSystem(currentSession.getConf());
      AuthorizationUtils.modifyAcl(aclEntries, targetPath, fs);
      LOG.info("Facl(" + facl + ") has been set for " + targetPath + " by " + username);
    } catch (TException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20440, ErrorMsgUtil.toString(tableName));
    } catch (IOException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20441);
    }
  }

  @Override
  public void revokeFacl(HivePrincipal hivePrincipal, String facl, String tableName, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    if (tableName == null) {
      throw new HiveAuthzPluginException(ErrorMsg.ERROR_20442);
    }

    // Get current database name
    SessionState currentSession = SessionState.get();
    String[] dbTable = AuthorizationUtils.getDatabaseTableName(currentSession, tableName);
    IMetaStoreClient metastoreClient = metastoreClientFactory.getHiveMetastoreClient();

    // authorize revoke facl:
    //   ADMIN can revoke facl from users.
    //   Users can revoke facl from himself.
    String username = authenticator.getUserName();
    HivePrivilegeObject hivePrivObject = new HivePrivilegeObject(
        HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW, dbTable[0], dbTable[1]);
    boolean isUserAdmin = guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);

    if (!isUserAdmin) {
      if (hivePrincipal == null) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20415);
      }
      // User cannot revoke facl from group
      if ((!hivePrincipal.getName().equals(username)) || hivePrincipal.getType() != HivePrincipal.HivePrincipalType.USER) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20416, ErrorMsgUtil.toString(hivePrincipal.getName()), ErrorMsgUtil.toString(hivePrincipal.getName()));
      }
    }

    try {
      Table table = metastoreClient.getTable(dbTable[0], dbTable[1]);
      Path targetPath = new Path(table.getSd().getLocation());
      FileSystem fs = targetPath.getFileSystem(currentSession.getConf());
      String principalName = hivePrincipal == null ? null : hivePrincipal.getName();
      String aclPrefix = null;
      if (hivePrincipal != null) {
        switch (hivePrincipal.getType()) {
          case USER:
            aclPrefix = "user";
            break;
          case GROUP:
            aclPrefix = "group";
            break;
          default:
            throw new HiveAuthzPluginException(ErrorMsg.ERROR_20439);
        }
      }
      AuthorizationUtils.removeUserAcl(principalName, aclPrefix, targetPath, fs);
      LOG.info("Facl of " + tableName + " has been removed from " + targetPath + " by " + username);
    } catch (TException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20440, ErrorMsgUtil.toString(tableName));
    } catch (IOException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20443);
    }
  }

  @Override
  public List<String> showFacl(HivePrincipal hivePrincipal, String tableName) throws HiveAuthzPluginException, HiveAccessControlException {
    if (tableName == null) {
      throw new HiveAuthzPluginException(ErrorMsg.ERROR_20444);
    }

    // Get current database name
    SessionState currentSession = SessionState.get();
    String[] dbTable = AuthorizationUtils.getDatabaseTableName(currentSession, tableName);
    IMetaStoreClient metastoreClient = metastoreClientFactory.getHiveMetastoreClient();

    // authorize show facl:
    //   ADMIN can show facl from users.
    //   Users can show facl from himself.
    String username = authenticator.getUserName();
    HivePrivilegeObject hivePrivObject = new HivePrivilegeObject(
        HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW, dbTable[0], dbTable[1]);
    boolean isUserAdmin = guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);

    if (!isUserAdmin) {
      if (hivePrincipal == null) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20417);
      }
      if ((!hivePrincipal.getName().equals(username)) || hivePrincipal.getType() != HivePrincipal.HivePrincipalType.USER) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20418, ErrorMsgUtil.toString(hivePrincipal.getName()), ErrorMsgUtil.toString(hivePrincipal.getName()));
      }
    }

    List<String> results;
    try {
      Table table = metastoreClient.getTable(dbTable[0], dbTable[1]);
      Path targetPath = new Path(table.getSd().getLocation());
      FileSystem fs = targetPath.getFileSystem(currentSession.getConf());
      String principalName = hivePrincipal == null ? null : hivePrincipal.getName();
      HivePrincipal.HivePrincipalType principalType = hivePrincipal == null ? null : hivePrincipal.getType();
      results = AuthorizationUtils.showUserAcl(principalName, principalType, targetPath, fs);
      String faclScope = (principalName == null) ? "all" : principalType.toString() + " " + principalName;
      LOG.info("Facl of " + faclScope + " on " + targetPath + " has been aquired by " + username);
    } catch (TException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20440, ErrorMsgUtil.toString(tableName));
    } catch (IOException e) {
      throw new HiveAuthzPluginException(e, ErrorMsg.ERROR_20445);
    }
    return results;
  }

  @Override
  public void grantRowPermission(String tableName, String rowPermission, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokeRowPermission(true, tableName, rowPermission);
  }

  @Override
  public void revokeRowPermission(String tableName) throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokeRowPermission(false, tableName, null);
  }

  @Override
  public List<HivePermissionInfo> showPermission(String tableName) throws HiveAuthzPluginException, HiveAccessControlException {
    if (tableName == null) {
      throw new HiveAuthzPluginException(ErrorMsg.ERROR_20446);
    }

    // Get current database name
    String[] dbTable = AuthorizationUtils.getDatabaseTableName(SessionState.get(), tableName);
    IMetaStoreClient metastoreClient = metastoreClientFactory.getHiveMetastoreClient();

    // authorize :
    //   ADMIN can show the row/column permission.
    String username = authenticator.getUserName();
    HivePrivilegeObject hivePrivObject = new HivePrivilegeObject(
        HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW, dbTable[0], dbTable[1]);
    boolean isUserAdmin = guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);

    if (!isUserAdmin) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20421);
    }

    List<HivePermissionInfo> results = new ArrayList<>();
    try {
      Table table = metastoreClient.getTable(dbTable[0], dbTable[1]);
      // get column permissions
      List<FieldSchema> columns = table.getSd().getCols();
      Map<String, String> colPerms = new LinkedHashMap<String, String>();
      for (FieldSchema f : columns) {
        String perm = f.getPermission();
        if (org.apache.commons.lang.StringUtils.isNotBlank(perm)) {
          colPerms.put(f.getName(), perm);
        }
      }
      // build hive permission info
      HivePermissionInfo permissionInfo = new HivePermissionInfo(tableName,
          table.isRlsEnabled(), table.getRowPermission(), table.isClsEnabled(), colPerms);
      results.add(permissionInfo);
    } catch (TException e) {
      e.printStackTrace();
    }
    return results;
  }

  @Override
  public void grantColumnPermission(String tableName, String columnName, String columnPermission, HivePrincipal grantorPrinc) throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokeColumnPermission(true, tableName, columnName, columnPermission);
  }

  @Override
  public void revokeColumnPermission(String tableName, String columnName) throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokeColumnPermission(false, tableName, columnName, null);
  }

  @Override
  public List<String> getAllRoles() throws HiveAuthzPluginException, HiveAccessControlException {
    // only user has ADMIN permission can list role
    String userName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(userName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20426, ErrorMsgUtil.toString(userName), ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
    }
    return guardianAuthorizer.getAllRoles();
  }

  @Override
  public List<HivePrivilegeInfo> showPrivileges(HivePrincipal principal, HivePrivilegeObject privObj) throws HiveAuthzPluginException, HiveAccessControlException {
    String currentUserName = authenticator.getUserName();
    IMetaStoreClient mClient = metastoreClientFactory.getHiveMetastoreClient();
    // First authorize the call
    Boolean isAdminOrOwner = guardianAuthorizer.isUserAdmin(currentUserName) || SQLAuthorizationUtils.isOwner(
            mClient, currentUserName, getCurrentRoleNames(), privObj);
    if (principal == null) {
      // only the admin and the owner are allowed to list privileges for any user
      if (!isAdminOrOwner) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20428, ErrorMsgUtil.toString(currentUserName), ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
      }
    } else {
      //principal is specified, authorize on it
      if (!isAdminOrOwner) {
        ensureShowGrantAllowed(principal);
      }
    }

    String principalName = principal == null ? null : principal.getName();
    HivePrincipal.HivePrincipalType hivePrincipalType = principal == null ? null : principal.getType();
    return guardianAuthorizer.listPrivilege(principalName, hivePrincipalType, privObj);
  }

  @Override
  public void setCurrentRole(String roleName) throws HiveAuthzPluginException, HiveAccessControlException {
    String userName = authenticator.getUserName();
    if ("admin".equals(roleName)) {
      if (!guardianAuthorizer.hasAdminRole(userName)) {
        throw new HiveAccessControlException(ErrorMsg.ERROR_20431, ErrorMsgUtil.toString(userName), "admin");
      }
    } else {
      if (!userBelongsToRole(roleName))
        throw new HiveAccessControlException(ErrorMsg.ERROR_20431, ErrorMsgUtil.toString(userName), ErrorMsgUtil.toString(roleName));
    }
  }

  @Override
  public List<String> getCurrentRoleNames() throws HiveAuthzPluginException {
    // only user has ADMIN permission can list role
    return guardianAuthorizer.getRoles(authenticator.getUserName());
  }

  @Override
  public List<HiveRoleGrant> getPrincipalGrantInfoForRole(String roleName) throws HiveAuthzPluginException, HiveAccessControlException {
    // only user has ADMIN permission can list role
    String currentUserName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(currentUserName)) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20427, ErrorMsgUtil.toString(currentUserName), ErrorMsgUtil.toString(ADMIN_ONLY_MSG), ErrorMsgUtil.toString(HAS_ADMIN_PRIV_MSG));
    }
    return guardianAuthorizer.getPrincipalGrantInfoForRole(roleName);
  }

  @Override
  public List<HiveRoleGrant> getRoleGrantInfoForPrincipal(HivePrincipal principal) throws HiveAuthzPluginException, HiveAccessControlException {
    String currentUserName = authenticator.getUserName();
    if (!guardianAuthorizer.isUserAdmin(currentUserName)) {
      ensureShowGrantAllowed(principal);
    }
    return guardianAuthorizer.getRoleGrantInfoForPrincipal(principal);
  }

  @Override
  public void applyAuthorizationConfigPolicy(HiveConf hiveConf) throws HiveAuthzPluginException {
    // ignore
  }

  private void grantOrRevokeRowPermission(Boolean isGrant, String tableName, String rowPermission)
      throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokePermission(isGrant, tableName, rowPermission, null, null, true);
  }

  private void grantOrRevokeColumnPermission(Boolean isGrant, String tableName, String columnName, String columnPermission)
      throws HiveAuthzPluginException, HiveAccessControlException {
    grantOrRevokePermission(isGrant, tableName, null, columnName, columnPermission, false);
  }

  private void grantOrRevokePermission(Boolean isGrant, String tableName, String rowPermission,
                                       String columnName, String columnPermission, Boolean setRowPermission)
      throws HiveAuthzPluginException, HiveAccessControlException {

    // Get current database name
    String[] dbTable = AuthorizationUtils.getDatabaseTableName(SessionState.get(), tableName);
    IMetaStoreClient metastoreClient = metastoreClientFactory.getHiveMetastoreClient();
    // authorize grant/revoke row permission:
    //   ADMIN can grant row permission on tables
    String username = authenticator.getUserName();
    HivePrivilegeObject hivePrivObject = new HivePrivilegeObject(
        HivePrivilegeObject.HivePrivilegeObjectType.TABLE_OR_VIEW, dbTable[0], dbTable[1]);
    boolean isUserAdmin = guardianAuthorizer.checkPermission(username, hivePrivObject, SQLPrivTypeGrant.ADMIN_PRIV);
    if (!isUserAdmin) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20422, ErrorMsgUtil.toString((isGrant ? "granted" : "revoked")));
    }

    // Get existing table
    Table oldTable;
    try {
      oldTable = metastoreClient.getTable(dbTable[0], dbTable[1]);
    } catch (TException e) {
      throw new HiveAccessControlException(ErrorMsg.ERROR_20423);
    }

    if (setRowPermission) {
      // Set row permission
      String perm = (rowPermission != null) ? rowPermission.replace("\n", " ") : "";
      oldTable.setRlsEnabled(isGrant);
      oldTable.setRowPermission(isGrant ? perm : null);
    } else {
      if (columnName == null && !isGrant) {
        // revoke all column permissions when column name is unset
        for (FieldSchema field : oldTable.getSd().getCols()) {
          field.setPermission(null);
        }
        oldTable.setClsEnabled(false);
      } else {
        // get column schema
        FieldSchema columnSchema = null;
        boolean existsOtherColPerm = false;
        for (FieldSchema field : oldTable.getSd().getCols()) {
          if (field.getName().equalsIgnoreCase(columnName)) {
            columnSchema = field;
            if (isGrant) {
              // found column for grant permission, so break the loop
              break;
            } else if(org.apache.commons.lang.StringUtils.isBlank(field.getPermission())) {
              // column permission is null or blank, no permission to revoke, so return
              return;
            }
          } else if (!isGrant && !existsOtherColPerm && org.apache.commons.lang.StringUtils.isNotBlank(field.getPermission())) {
            existsOtherColPerm = true;
          }
        }
        if (columnSchema != null) {
          // set column permission
          String perm = (org.apache.commons.lang.StringUtils.isNotBlank(columnPermission)) ? columnPermission.replace("\n", " ") : null;
          columnSchema.setPermission(isGrant ? perm : null);
          // disable column level security only when revoking and no other column has been set column permission.
          if (!isGrant && !existsOtherColPerm) {
            oldTable.setClsEnabled(false);
          } else {
            oldTable.setClsEnabled(true);
          }
        } else {
          throw new HiveAccessControlException(ErrorMsg.ERROR_20424, ErrorMsgUtil.toString(columnName));
        }
      }
    }

    // Commit row permission to metastore
    try {
      metastoreClient.alter_table(dbTable[0], dbTable[1], oldTable);
    } catch (TException e) {
      throw new HiveAccessControlException(e, ErrorMsg.ERROR_20425);
    }
  }

  private List<HivePrivilege> expandAndValidatePrivileges(List<HivePrivilege> hivePrivileges, HivePrivilegeObject.HivePrivilegeObjectType objectType)
          throws HiveAuthzPluginException {
    // expand ALL privileges, if any
    hivePrivileges = expandAllPrivileges(hivePrivileges, objectType);
    validatePrivileges(hivePrivileges, objectType);
    return hivePrivileges;
  }

  private List<HivePrivilege> expandAllPrivileges(List<HivePrivilege> hivePrivileges, HivePrivilegeObject.HivePrivilegeObjectType objectType)
          throws HiveAuthzPluginException {
    Set<HivePrivilege> hivePrivSet = new HashSet<HivePrivilege>();
    for (HivePrivilege hivePrivilege : hivePrivileges) {
      if (hivePrivilege.getName().equals(ALL)) {
        // expand to all supported privileges
        for (GuardianSQLPrivilegeType privType : GuardianSQLPrivilegeType.getAllPrivileges(objectType)) {
          hivePrivSet.add(new HivePrivilege(privType.name(), hivePrivilege.getColumns()));
        }
      } else {
        hivePrivSet.add(hivePrivilege);
      }
    }
    return new ArrayList<>(hivePrivSet);
  }

  private void ensureShowGrantAllowed(HivePrincipal principal)
          throws HiveAccessControlException, HiveAuthzPluginException {
    // if user is not an admin user, allow the request only if the user is
    // requesting for privileges for themselves or a role they belong to
    String currentUserName = authenticator.getUserName();
    switch (principal.getType()) {
      case USER:
        if (!principal.getName().equals(currentUserName)) {
          throw new HiveAccessControlException(ErrorMsg.ERROR_20429, ErrorMsgUtil.toString(currentUserName), ErrorMsgUtil.toString(principal.getName()), ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
        }
        break;
      case ROLE:
        if (!userBelongsToRole(principal.getName())) {
          throw new HiveAccessControlException(ErrorMsg.ERROR_20430, ErrorMsgUtil.toString(currentUserName), ErrorMsgUtil.toString(principal.getName()), ErrorMsgUtil.toString(ADMIN_ONLY_MSG));
        }
        break;
      default:
        throw new AssertionError("Unexpected principal type " + principal.getType());
    }
  }

  /**
   * @param roleName
   * @return true if roleName is the name of one of the roles (including the role hierarchy)
   * that the user belongs to.
   * @throws HiveAuthzPluginException
   */
  private boolean userBelongsToRole(String roleName) throws HiveAuthzPluginException {
    for (String role : guardianAuthorizer.getRoles(authenticator.getUserName())) {
      // set to one of the roles user belongs to.
      if (role.equalsIgnoreCase(roleName)) {
        return true;
      }
    }
    return false;
  }

  private static void validatePrivileges(List<HivePrivilege> hivePrivileges, HivePrivilegeObject.HivePrivilegeObjectType objectType)
          throws HiveAuthzPluginException {
    for (HivePrivilege hivePrivilege : hivePrivileges) {
      if (hivePrivilege.getColumns() != null && hivePrivilege.getColumns().size() != 0) {
        throw new HiveAuthzPluginException(ErrorMsg.ERROR_20392, ErrorMsgUtil.toString(hivePrivilege));
      }
      //try converting to the enum to verify that this is a valid privilege type
      GuardianSQLPrivilegeType type = GuardianSQLPrivilegeType.getRequirePrivilege(hivePrivilege.getName());

      if (!GuardianSQLPrivilegeType.getAllPrivileges(objectType).contains(type)) {
        throw new HiveAuthzPluginException(ErrorMsg.ERROR_20393, ErrorMsgUtil.toString(hivePrivilege), ErrorMsgUtil.toString(objectType));
      }
    }
  }

  private List<QuotaVo> convertToQuotaObjs(HivePrincipal hivePrincipal, String database) {
    if (hivePrincipal == null) {
      return convertToQuotaObjs((List<HivePrincipal>) null, database);
    } else {
      return convertToQuotaObjs(Collections.singletonList(hivePrincipal), database);
    }
  }
  private List<QuotaVo> convertToQuotaObjs(List<HivePrincipal> hivePrincipals, String database) {
    List<QuotaVo> quotaVos = new ArrayList<>();

    // datasource
    List<String> datasource = new ArrayList<>();
    if (database.equals("__TEMP_SPACE__")) {
      datasource.add("TEMPORARY");
    } else {
      datasource.add("DATABASE");
      datasource.add(database);
    }

    if (hivePrincipals == null || hivePrincipals.isEmpty()) {
      QuotaVo quotaVo = new QuotaVo();
      quotaVo.setComponent(component);
      quotaVo.setDataSource(datasource);
      quotaVos.add(quotaVo);
    } else {
      for (HivePrincipal princ : hivePrincipals) {
        datasource.add(princ.getName());
        QuotaVo quotaVo = new QuotaVo();
        quotaVo.setComponent(component);
        quotaVo.setDataSource(datasource);
        quotaVos.add(quotaVo);
      }
    }
    return quotaVos;
  }

  @Override
  public List<String> filterDatabaseByPrivileges(List<String> dbs) throws HiveAuthzPluginException {
    if (!filterShowDescs || CollectionUtils.isEmpty(dbs)) {
      return dbs;
    }

    String username = authenticator.getUserName();
    // result from guardian
    List<String> resultFromGuardian = guardianAuthorizer.filterDatabaseByPrivileges(username, dbs);
    try {
      // result from metastore
      List<String> resultFromMetastore = metastoreClientFactory.getHiveMetastoreClient().getDatabasesWithUser(".*", username, true);
      Set<String> mergedSet = new HashSet<>(resultFromGuardian);
      if (CollectionUtils.isNotEmpty(resultFromMetastore)) {
        mergedSet.addAll(resultFromMetastore);
      }
      mergedSet.retainAll(dbs);
      return new ArrayList<>(mergedSet);
    } catch (TException e) {
      LOG.error("Fail to filter databases by privileges from Metastore with user: [{}]", username, e);
      throw new HiveAuthzPluginException(e);
    }
  }

  @Override
  public List<String> filterTablesByPrivileges(String db, List<String> tables) throws HiveAuthzPluginException {
    if (!filterShowDescs || CollectionUtils.isEmpty(tables)) {
      return tables;
    }

    String username = authenticator.getUserName();
    // result from guardian
    List<String> resultFromGuardian = guardianAuthorizer.filterTablesByPrivileges(username, db, tables);
    try {
      // result from metastore
      List<String> resultFromMetastore = metastoreClientFactory.getHiveMetastoreClient().getTablesWithUser(db, ".*", username);
      Set<String> mergedSet = new HashSet<>(resultFromGuardian);
      if (CollectionUtils.isNotEmpty(resultFromMetastore)) {
        mergedSet.addAll(resultFromMetastore);
      }
      mergedSet.retainAll(tables);
      return new ArrayList<>(mergedSet);
    } catch (TException e) {
      LOG.error("Fail to filter tables by privileges from Metastore with user: [{}]", username, e);
      throw new HiveAuthzPluginException(e);
    }
  }
}
