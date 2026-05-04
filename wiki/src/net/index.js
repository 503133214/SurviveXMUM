import axios from 'axios';
import {ElMessage} from "element-plus";
const authItemName="token";
const defaultFailure=(message,code,url)=>{
    console.warn(`请求地址：${url},状态码：${code},错误信息: ${message}`)
    ElMessage.warning(message)
}
const defaultError=(err)=>{
    console.warn(err)
    ElMessage.warning("发生了一些错误，请联系管理员")
}
function parseJWTExpire(token) {
    try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        return payload.exp * 1000; // JWT exp 是秒，转换为毫秒
    } catch (e) {
        return Date.now() + 24 * 60 * 60 * 1000; // 解析失败则使用默认24小时
    }
}
function storeAccessToken(token) {
    const expire = parseJWTExpire(token);
    const authObj = { token: token, expire: expire };
    const str = JSON.stringify(authObj);
    localStorage.setItem(authItemName, str);
}
function takeAccessToken(){
    const token=localStorage.getItem(authItemName);
    if(!token){
        return null;
    }
    const authObj=JSON.parse(token);
    if(authObj.expire<=Date.now()){
        deleteAccessToken();
        ElMessage.warning("登录已过期，请重新登录")
        return null
    }
    return authObj.token; // 返回有效的token
}
function deleteAccessToken(){
    localStorage.removeItem(authItemName)
}
function internalPost(url,data,headers,success,failure,error=defaultError){
    axios.post(url, data, { headers: headers }).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function internalGet(url,headers,success,failure,error=defaultError){
    axios.get(url, { headers: headers}).then(({ data }) => {
        if (data.code === 0) {
            success(data.data)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}
function accessHeader(){
    const token = takeAccessToken()
    return token ?{
        'Authorization': `Bearer ${token}`
    }:{}
}
function get(url,success,failure=defaultFailure){
    internalGet(url,accessHeader(),success,failure)
}
function post(url,data,success,failure=defaultFailure){
    internalPost(url,data,accessHeader(),success,failure)
}
function unauthorized(){
    return !takeAccessToken()
}
function login(username,code,success,failure=defaultFailure){
    internalPost("/login/email",{
        userEmail: username,
        code: code
    },{
        'Content-Type': 'application/json'
    },(data)=>{
        storeAccessToken(data)
        ElMessage.success(`登录成功,欢迎${username}！`)
        success(data)
    },failure)
}
function logout(success, failure = defaultFailure, error = defaultError) {
    // 检查token是否存在且有效
    if (!takeAccessToken()) {
        failure('未找到登录令牌或令牌已过期', -1, '/login/logout');
        return;
    }
    internalPost(
        '/login/logout',
        {},
        accessHeader(),
        (responseData) => {
            deleteAccessToken(); // 登出成功后删除token
            success(responseData);
        },
        failure,
        error
    );
}
export {get,unauthorized,post,accessHeader,login,logout,takeAccessToken}