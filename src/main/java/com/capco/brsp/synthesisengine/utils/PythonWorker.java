package com.capco.brsp.synthesisengine.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
public final class PythonWorker implements Closeable {
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;

    public PythonWorker(String pythonExe, String preloadCsv) throws IOException {
        String b64 = Base64.getEncoder().encodeToString(PY_SERVER_WRAPPER.getBytes(StandardCharsets.UTF_8));
        String launcher = "import base64;exec(base64.b64decode('" + b64 + "'))";

        ProcessBuilder pb = new ProcessBuilder(pythonExe, "-u", "-c", launcher);
        pb.redirectErrorStream(false);
        if (preloadCsv != null) {
            pb.environment().put("PY_PRELOAD", preloadCsv);
        }

        pb.environment().put("PY_STATE_VARS", "");

        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        Thread stderrPump = new Thread(() -> {
            try (BufferedReader err =
                         new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    log.info("{}", line);
                }
            } catch (IOException ignored) {}
        }, "python-worker-stderr");
        stderrPump.setDaemon(true);
        stderrPump.start();
    }

    public Map<String, Object> exec(String code) throws IOException {
        return exec(code, Map.of());
    }

    public Map<String, Object> exec(String code, Map<String, Object> globals) throws IOException {
        String codeB64 = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));

        var req = new java.util.LinkedHashMap<String, Object>();
        req.put("code_b64", codeB64);

        if (globals != null && !globals.isEmpty()) req.put("globals", globals);

        String lineOut = JsonUtils.writeAsJsonString(req, false);
        stdin.write(lineOut);
        stdin.write("\n");
        stdin.flush();

        String line = stdout.readLine();
        if (line == null) throw new EOFException("Python worker terminated");
        return JsonUtils.readAsMap(line);
    }

    @Override
    public void close() throws IOException {
        try {
            stdin.write("__shutdown__\n");
            stdin.flush();
        } catch (IOException ignored) {
        }

        try {
            stdin.close();
        } catch (IOException ignored) {
        }

        try {
            stdout.close();
        } catch (IOException ignored) {
        }

        process.destroy();
    }

    private static final String PY_SERVER_WRAPPER =
            """
                    import contextlib, io, os, sys, ast, json, types, traceback, math, base64
                    
                    DF_ORIENT = os.environ.get('PY_DF_ORIENT', 'records')
                    REQ_DEFAULT = os.environ.get('PY_STATE_VARS')
                    REQ_DEFAULT = [s.strip() for s in REQ_DEFAULT.split(',')] if REQ_DEFAULT else None
                    
                    def _json_safe_number(x):
                        if isinstance(x, float):
                            return x if math.isfinite(x) else None
                        return x
                    
                    def to_jsonable(x, _seen=None):
                        if _seen is None:
                            _seen=set()
                    
                        oid=id(x)
                    
                        if oid in _seen:
                            return '<recursion>'
                    
                        _seen.add(oid)
                        if x is None or isinstance(x,(bool,int,float,str)):
                            return _json_safe_number(x)
                    
                        fn = getattr(x,'isoformat',None)
                        if callable(fn):
                            try:
                                return fn()
                            except:
                                pass
                    
                        for tname in ('Decimal','UUID','Path'):
                            if x.__class__.__name__==tname or x.__class__.__module__.endswith(tname.lower()):
                                try:
                                    return str(x)
                                except:
                                    return repr(x)
                    
                        item = getattr(x,'item',None)
                        if callable(item):
                            try:
                                return to_jsonable(item(),_seen)
                            except:
                                pass
                    
                        tolist = getattr(x,'tolist',None)
                        if callable(tolist):
                            try:
                                return to_jsonable(tolist(),_seen)
                            except:
                                pass
                    
                        to_dict = getattr(x,'to_dict',None)
                        if callable(to_dict):
                            try:
                                return to_jsonable(to_dict(orient=DF_ORIENT),_seen)
                            except TypeError:
                                try:
                                    return to_jsonable(to_dict(),_seen)
                                except:
                                    pass
                    
                        try:
                            from collections.abc import Mapping
                            if isinstance(x, Mapping):
                                return { (k if isinstance(k,str) else str(k)) : to_jsonable(v,_seen) for k,v in x.items() }
                        except:
                            pass
                    
                        if isinstance(x,(list,tuple,set)):
                            return [to_jsonable(i,_seen) for i in x]
                        return repr(x)
                    
                    def _transform_last_expr_to_assign(mod, name='__return__'):
                        if not getattr(mod,'body',None):
                            return
                    
                        last = mod.body[-1]
                        if isinstance(last, ast.Expr):
                            assign = ast.Assign(targets=[ast.Name(id=name, ctx=ast.Store())], value=last.value)
                            ast.copy_location(assign,last)
                            mod.body[-1] = assign
                            ast.fix_missing_locations(mod)
                    
                    def run_once(code_str, req, orient, pre_vars):
                        ns = {}
                        
                        if isinstance(pre_vars, dict):
                                ns.update(pre_vars)
                        
                        orig_stdout = sys.stdout
                        try:
                            tree = ast.parse(code_str, filename='<user>', mode='exec')
                            _transform_last_expr_to_assign(tree,'__return__')
                            tree = ast.fix_missing_locations(tree)
                            
                            with contextlib.redirect_stdout(sys.stderr):
                                exec(compile(tree,'<user>','exec'), ns, ns)
                            
                            def include_name(k,v):
                                if k.startswith('_'):
                                    return False
                    
                                if req is not None and k not in req:
                                    return False
                    
                                if isinstance(v,(types.ModuleType,types.FunctionType,type)):
                                    return False
                    
                                return True
                    
                            state = {k: to_jsonable(v) for k,v in ns.items() if include_name(k,v)}
                            ret = ns.get('__return__',None)
                    
                            orig_stdout.write(json.dumps({'return': to_jsonable(ret), 'vars': state}, ensure_ascii=False) + '\\n')
                            orig_stdout.flush()
                        except BaseException as e:
                            state = {}
                            if 'ns' in locals():
                                def include_name(k,v):
                                    if k.startswith('_'):
                                        return False
                    
                                    if req is not None and k not in req:
                                        return False
                    
                                    if isinstance(v,(types.ModuleType,types.FunctionType,type)):
                                        return False
                    
                                    return True
                                state = {k: to_jsonable(v) for k,v in ns.items() if include_name(k,v)}
                    
                            err = {'type': type(e).__name__, 'message':str(e), 'trace': '\\n'.join(traceback.format_exception(e)).rstrip()}
                    
                            orig_stdout.write(json.dumps({'return': None, 'vars': state, 'error': err}, ensure_ascii=False) + '\\n')
                            orig_stdout.flush()
                    
                    def main():
                        preload = os.environ.get('PY_PRELOAD','')
                        if preload:
                            for name in preload.split(','):
                                name = name.strip()
                                if name:
                                    try:
                                        __import__(name)
                                    except Exception:
                                        pass
                        while True:
                            line = sys.stdin.readline()
                            if not line:
                                break
                            line = line.strip()
                    
                            if not line:
                                continue
                            if line == '__shutdown__':
                                break
                    
                            try: 
                                msg = json.loads(line)
                            except Exception: 
                                continue
                    
                            code_b64 = msg.get('code_b64','')
                            code = base64.b64decode(code_b64).decode('utf-8', 'replace') if code_b64 else msg.get('code','')
                    
                            req = msg.get('vars', REQ_DEFAULT)
                            orient = msg.get('df_orient', DF_ORIENT)
                            globals()['DF_ORIENT'] = orient
                            
                            pre_vars = msg.get('globals') or msg.get('params') or {}
                    
                            run_once(code, req, orient, pre_vars)
                    
                    if __name__=='__main__':
                        main()
                    """;
}
