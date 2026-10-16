
#include <unicode/ucol.h>
#include <unicode/uversion.h>
#include <unicode/utypes.h>

static int synthigyHumanCmp(void *p, int n1, const void *z1, int n2, const void *z2){
  UErrorCode status = U_ZERO_ERROR;
  UCollationResult r;
  int m = memcmp(z1, z2, n1<n2 ? n1 : n2);
  if( m==0 && n1==n2 ) return 0;
  r = ucol_strcollUTF8((UCollator*)p, (const char*)z1, n1, (const char*)z2, n2, &status);
  if( r==UCOL_LESS ) return -1;
  if( r==UCOL_GREATER ) return 1;
  return m ? m : n1 - n2;
}

static UCollator *synthigyHumanOpen(UErrorCode *status){
  UCollator *c = ucol_open("", status);
  if( U_SUCCESS(*status) ) ucol_setAttribute(c, UCOL_NUMERIC_COLLATION, UCOL_ON, status);
  if( U_FAILURE(*status) && c ){
    ucol_close(c);
    c = 0;
  }
  return c;
}

static void synthigyHumanDel(void *p){ ucol_close((UCollator*)p); }

static void synthigyHumanVersion(sqlite3_context *ctx, int argc, sqlite3_value **argv){
  UErrorCode status = U_ZERO_ERROR;
  UCollator *c = synthigyHumanOpen(&status);
  UVersionInfo coll, icu;
  char a[U_MAX_VERSION_STRING_LENGTH], b[U_MAX_VERSION_STRING_LENGTH];
  if( U_FAILURE(status) ){
    sqlite3_result_error(ctx, u_errorName(status), -1);
    return;
  }
  ucol_getVersion(c, coll);
  ucol_close(c);
  u_getVersion(icu);
  u_versionToString(coll, a);
  u_versionToString(icu, b);
  sqlite3_result_text(ctx, sqlite3_mprintf("%s/icu-%s", a, b), -1, sqlite3_free);
}

static int synthigyHumanInit(sqlite3 *db, char **pzErr, const sqlite3_api_routines *pApi){
  UErrorCode status = U_ZERO_ERROR;
  UCollator *c = synthigyHumanOpen(&status);
  int rc;
  if( U_FAILURE(status) ){
    *pzErr = sqlite3_mprintf("HUMAN collation: ICU %s", u_errorName(status));
    return SQLITE_ERROR;
  }
  rc = sqlite3_create_collation_v2(db, "HUMAN", SQLITE_UTF8, (void*)c, synthigyHumanCmp, synthigyHumanDel);
  if( rc!=SQLITE_OK ){
    ucol_close(c);
    return rc;
  }
  return sqlite3_create_function(db, "human_version", 0, SQLITE_UTF8|SQLITE_DETERMINISTIC|SQLITE_INNOCUOUS,
                                 0, synthigyHumanVersion, 0, 0);
}

int synthigy_extra_init(const char *z){
  return sqlite3_auto_extension((void(*)(void))synthigyHumanInit);
}
