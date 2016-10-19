# Validating configuration data

## Problem Statement

Currently, when a TK app fails, the log usually contains something like this:

```
Exception in thread "main" clojure.lang.ExceptionInfo: Value does not match schema: {:client {:foo disallowed-key}} {:type :schema.core/error, :schema {:data-dir (pred "String or File"), :repos {Keyword (conditional puppetlabs.enterprise.file-sync-common/fn--26912 {(optional-key :honor-gitignore) Bool, (optional-key :staging-dir) (pred "String or File"), (optional-key :submodules-dir) (pred "String or File"), (optional-key :auto-commit) Bool, :client-active (pred false?), (optional-key :live-dir) (pred "String or File")} clojure.core$constantly/fn--4383 {(optional-key :honor-gitignore) Bool, (optional-key :staging-dir) (pred "String or File"), (optional-key :submodules-dir) (pred "String or File"), (optional-key :auto-commit) Bool, (optional-key :client-active) (pred true?), :live-dir (pred "String or File")})}, :client {:poll-interval Int, :server-repo-url java.lang.String, :server-api-url java.lang.String, #schema.core.OptionalKey{:k :ssl-cert} java.lang.String, #schema.core.OptionalKey{:k :ssl-key} java.lang.String, #schema.core.OptionalKey{:k :ssl-ca-cert} java.lang.String, #schema.core.OptionalKey{:k :enable-forceful-sync} java.lang.Boolean, #schema.core.OptionalKey{:k :stream-file-threshold} Int}, #schema.core.OptionalKey{:k :client-certnames} [java.lang.String], #schema.core.OptionalKey{:k :preserve-deleted-submodules} java.lang.Boolean}, :value {:repos {:puppet-code {:submodules-dir "environments", :live-dir "./target/file-sync-test/live-dir", :staging-dir "./target/file-sync-test/staging-dir"}, :ca {:auto-commit true, :staging-dir "./target/file-sync-test/ca-staging", :client-active false}}, :client {:server-repo-url "http://localhost:8140/file-sync-git", :foo "bar", :server-api-url "http://localhost:8140/file-sync/v1", :poll-interval 5}, :data-dir "./target/file-sync-test/data-dir", :preserve-deleted-submodules false}, :error {:client {:foo disallowed-key}}}, compiling:(/private/var/folders/44/dd9x_8fj5xj90xs8z_s35dqc0000gq/T/form-init5378394777279943032.clj:1:125)
	at clojure.lang.Compiler.load(Compiler.java:7239)
	at clojure.lang.Compiler.loadFile(Compiler.java:7165)
	at clojure.main$load_script.invoke(main.clj:275)
	at clojure.main$init_opt.invoke(main.clj:280)
	at clojure.main$initialize.invoke(main.clj:308)
	at clojure.main$null_opt.invoke(main.clj:343)
	at clojure.main$main.doInvoke(main.clj:421)
	at clojure.lang.RestFn.invoke(RestFn.java:421)
	at clojure.lang.Var.invoke(Var.java:383)
	at clojure.lang.AFn.applyToHelper(AFn.java:156)
	at clojure.lang.Var.applyTo(Var.java:700)
	at clojure.main.main(main.java:37)
Caused by: clojure.lang.ExceptionInfo: Value does not match schema: {:client {:foo disallowed-key}} {:type :schema.core/error, :schema {:data-dir (pred "String or File"), :repos {Keyword (conditional puppetlabs.enterprise.file-sync-common/fn--26912 {(optional-key :honor-gitignore) Bool, (optional-key :staging-dir) (pred "String or File"), (optional-key :submodules-dir) (pred "String or File"), (optional-key :auto-commit) Bool, :client-active (pred false?), (optional-key :live-dir) (pred "String or File")} clojure.core$constantly/fn--4383 {(optional-key :honor-gitignore) Bool, (optional-key :staging-dir) (pred "String or File"), (optional-key :submodules-dir) (pred "String or File"), (optional-key :auto-commit) Bool, (optional-key :client-active) (pred true?), :live-dir (pred "String or File")})}, :client {:poll-interval Int, :server-repo-url java.lang.String, :server-api-url java.lang.String, #schema.core.OptionalKey{:k :ssl-cert} java.lang.String, #schema.core.OptionalKey{:k :ssl-key} java.lang.String, #schema.core.OptionalKey{:k :ssl-ca-cert} java.lang.String, #schema.core.OptionalKey{:k :enable-forceful-sync} java.lang.Boolean, #schema.core.OptionalKey{:k :stream-file-threshold} Int}, #schema.core.OptionalKey{:k :client-certnames} [java.lang.String], #schema.core.OptionalKey{:k :preserve-deleted-submodules} java.lang.Boolean}, :value {:repos {:puppet-code {:submodules-dir "environments", :live-dir "./target/file-sync-test/live-dir", :staging-dir "./target/file-sync-test/staging-dir"}, :ca {:auto-commit true, :staging-dir "./target/file-sync-test/ca-staging", :client-active false}}, :client {:server-repo-url "http://localhost:8140/file-sync-git", :foo "bar", :server-api-url "http://localhost:8140/file-sync/v1", :poll-interval 5}, :data-dir "./target/file-sync-test/data-dir", :preserve-deleted-submodules false}, :error {:client {:foo disallowed-key}}}
	at schema.core$validator$fn__2807.invoke(core.clj:155)
	at schema.core$validate.invoke(core.clj:164)
	at puppetlabs.enterprise.services.file_sync_client.file_sync_client_service$reify__31361$service_fnk__5269__auto___positional$reify__31380.init(file_sync_client_service.clj:28)
	at puppetlabs.trapperkeeper.services$eval5071$fn__5072$G__5059__5075.invoke(services.clj:8)
	at puppetlabs.trapperkeeper.services$eval5071$fn__5072$G__5058__5079.invoke(services.clj:8)
	at puppetlabs.trapperkeeper.internal$eval13803$run_lifecycle_fn_BANG___13810$fn__13811.invoke(internal.clj:200)
	at puppetlabs.trapperkeeper.internal$eval13803$run_lifecycle_fn_BANG___13810.invoke(internal.clj:183)
	at puppetlabs.trapperkeeper.internal$eval13832$run_lifecycle_fns__13837$fn__13838.invoke(internal.clj:234)
	at puppetlabs.trapperkeeper.internal$eval13832$run_lifecycle_fns__13837.invoke(internal.clj:211)
	at puppetlabs.trapperkeeper.internal$eval14303$build_app_STAR___14312$fn$reify__14322.init(internal.clj:584)
	at puppetlabs.trapperkeeper.internal$eval14349$boot_services_for_app_STAR__STAR___14356$fn__14357$fn__14359.invoke(internal.clj:612)
	at puppetlabs.trapperkeeper.internal$eval14349$boot_services_for_app_STAR__STAR___14356$fn__14357.invoke(internal.clj:611)
	at puppetlabs.trapperkeeper.internal$eval14349$boot_services_for_app_STAR__STAR___14356.invoke(internal.clj:605)
	at clojure.core$partial$fn__4529.invoke(core.clj:2499)
	at puppetlabs.trapperkeeper.internal$eval13871$initialize_lifecycle_worker__13882$fn__13883$fn__13970$state_machine__11842__auto____13971$fn__13973.invoke(internal.clj:251)
	at puppetlabs.trapperkeeper.internal$eval13871$initialize_lifecycle_worker__13882$fn__13883$fn__13970$state_machine__11842__auto____13971.invoke(internal.clj:251)
	at clojure.core.async.impl.ioc_macros$run_state_machine.invoke(ioc_macros.clj:1011)
	at clojure.core.async.impl.ioc_macros$run_state_machine_wrapped.invoke(ioc_macros.clj:1015)
	at clojure.core.async$ioc_alts_BANG_$fn__12010.invoke(async.clj:378)
	at clojure.core.async$do_alts$fn__11956$fn__11959.invoke(async.clj:247)
	at clojure.core.async.impl.channels.ManyToManyChannel$fn__6777$fn__6778.invoke(channels.clj:95)
	at clojure.lang.AFn.run(AFn.java:22)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
	at java.lang.Thread.run(Thread.java:745)
```

Ahhh, my poor eyes!  That's terrible.  Instead, wouldn't it be nice if the log
just said something like this?
```
Configuration data contains invalid unexpected entry:
Key 'foo' is not allowed under section 'client'
```


## Solution

When TK starts up it invokes a function on each service at some
point (TODO: should this happen before or after `init`?)  This function
is run on each service sequentially; if any service reports that it has
invalid configuration data, Trapperkeeper logs this in a human-friendly format
and then exists before starting any of the services.  All of the services are
checked, and if the configuration data is invalid for multiple services,
this will be reported.

Options to implement this solution:

### uno

`check-config` returns `nil` if the service has valid configuration data or
a non-nil value (TODO would need to define API for this) when the service's
configuration data is invalid.

This seems like the worst option because it would require making up an API
for what a configuration error should look like, which is probably more work
and more cognitive burden for users that just reusing/parsing schema errors.

### dos

`required-config` returns a Plumatic schema which defines the structure of a
valid configuration map for the service.

### tres

`required-config-keys` returns a sequence which describes the keys which must
be present in the configuration data for it to be valid for the given service.
Each element in the sequence must be either a keyword or a sequence which
describes a nested map.  For example, if the service requires configuration
such as:
```hocon
foo : 1
bar : {
  baz : 2
}
a : {
  b : {
    c : "some value"
  }
}
```
... then `required-config-keys` should return `[:foo [:bar :baz] [:a :b :c]]`

Note that this approach takes a narrower approach than the previous two options
and tackles a simpler problem: only validating required keys, doesn't check
values, types of values, or account for disallowed keys ... which, quite
unfortunately, is the example I used in the problem statement.  Darn.
TODO is this good enough?

### Are there other options?

TODO well, are there?!

## Future Ideas

Add first-class support for transforming config data
(once it's determined to be valid).
