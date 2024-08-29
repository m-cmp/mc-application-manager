// import { checkIP } from './validate'




// const _validateAlphanumericUnderbarHyphen = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	const reg = /^[A-z0-9_-]{1,50}$/

// 	if (!reg.test(value)) {
// 		callback(new Error(t("validation.alphanumericUnderbarHyphen")));
// 	} else {
// 		callback()
// 	}
// }

// const _validateAlphanumericUnderbarHyphenSpace = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	const reg = /^[A-z0-9_-\s]{1,50}$/

// 	if (!reg.test(value)) {
// 		callback(new Error(t("validation.alphanumericUnderbarHyphen")));
// 	} else {
// 		callback()
// 	}
// }

// const _validateAlphaNumeric = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	const reg = /^[0-9a-zA-Z]+$/
// 	if (!reg.test(value)) {
// 		callback(new Error(t("validation.alphanumeric")));
// 	} else {
// 		callback()
// 	}
// }
export const _validateAlphaNumericHyphen= (rule, value, callback) => {
	const reg = /^[0-9a-zA-Z-]+$/
	if (!reg.test(value)) {
		callback(new Error("규칙에 맞게 입력해주세요.(영문자, 숫자, (-)만 가능)"));
	} else {
		callback()
	}
}

// const _validateDomain = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	const reg = /^[^((http(s?))\:\/\/)]([0-9a-zA-Z\-]+\.)+[a-zA-Z]{2,6}(\:[0-9]+)?(\/\S*)?$/
// 	if (!reg.test(value)) {
// 		callback(new Error(t("validation.domain")));
// 	} else {
// 		callback()
// 	}

// }

// const _validateOpenShiftAppName = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	//alphanumeric (a-z, 0-9) 최대 16 character. 첫 글자는 a-z 만 허용. ‘-’ 는 허용되는데 처음과 끝은 안됨
//   const reg = /^[A-Za-z]{1}[A-z0-9_-]{1,16}[A-Za-z0-9]$/
// 	if (!reg.test(value) || value.length > 16) {
// 		callback(new Error(t("validation.openShiftAppNameMsg")));
// 	} else {
// 		callback()
// 	}
// }

// const _validateAppName = (rule, value, callback) => {
//   const { t }  = i18n.global;
// 	//alphanumeric (a-z, 0-9) 최대 16 character. 첫 글자는 a-z 만 허용. ‘-’ 는 허용되는데 처음과 끝은 안됨
//   const reg = /^[a-z]{1}[a-z0-9_-]{1,16}$/
// 	if (!reg.test(value) || value.length > 16) {
// 		callback(new Error(t("validation.appNameMsg")));
// 	} else {
// 		callback()
// 	}
// }


// const _validateName = (rule, value, callback) => {
//     const { t }  = i18n.global;
//     if (value==null || !value.toString().length) {
//         callback(new Error(t("validation.msgEmtpyInput")));
//     } else {
//         callback()

//     }

// }

// const _valdateLimitLength=(rule, value, callback)=>{
//     const { t }  = i18n.global;
//   	if (value.length > rule.length) {
//         callback(new Error(t("validation.limitLength")));
// 	} else {
// 		callback()
// 	}
// }

export const _validateLength = (rule, value, callback) => {
    if (value.length < rule.length) {
        if (rule.length == 2) {
            callback(new Error("2자 이상 입력해주세요."));
            return
        }
        if (rule.length == 3) {
            callback(new Error("3자 이상 입력해주세요."));
            return
        }
        if (rule.length == 4) {
            callback(new Error("4자 이상 입력해주세요."));
            return
        }
        callback(new Error("내용을 입력해주세요."));
    } else {
      callback()
    }
}

// const _validateIp = (rule, value, callback) => {
//   const { t }  = i18n.global;
//     if (checkIP(value) == false) {
//         callback(new Error(t("validation.msgIP")));
//   } else {
//     callback()
//   }
// }

// const _validateEmail = (rule, value, callback) => {
//   const { t }  = i18n.global;
//   const reg = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/

//   if (reg.test(value) == false) {
//     callback(new Error(t("validation.msgEmail")));
//   } else {
//     callback()
//   }
// }

// const _validateUserPassword = (rule, value, callback) => {
//   const { t }  = i18n.global;
//   const passwordPatten = /^(?=.*?[A-Z])(?=.*?[0-9]).{8,16}$/
//     if (passwordPatten.test(value) == false) {
//         let msgType = rule.msgType || "full";
//         if(msgType=="full")
//             callback(new Error(t("validation.msgPw")));
//         else
//             callback(new Error(t("validation.msgSimplePw")));

//   } else {
//     callback()
//   }
// }

// const _validatePackageName = (rule, value, callback) => {
//     const { t }  = i18n.global;
//     const packageNamePatten = /^([A-Za-z]{1}[A-Za-z\d_]*\.)*[A-Za-z][A-Za-z\d_]*$/
//     if (packageNamePatten.test(value) == false) {
//         callback(new Error(t("validation.msgPackageName")));

//     } else {
//         callback()
//     }
// }


// const _validateUserLengthPassword = (rule, value, callback) => {
//   const { t }  = i18n.global;
//     let temp = value.toString();
//         if (temp.length>=8 && temp.length<=16) {
//             callback()
//         } else {
//             callback(new Error(t("validation.msgPw2")));
//         }
//   }

// const _validateSelect = (rule, value, callback) => {
//   const { t }  = i18n.global;
//   if (value=="" || value==undefined) {
//     callback(new Error(t("validation.msgSelect")));
//   } else {
//     callback()
//   }
// }

// const _validateSelect2 = (rule, value, callback) => {
//   const { t }  = i18n.global;
//     if (parseInt(value)==-1) {
//       callback(new Error(t("validation.msgSelect")));
//     } else {
//       callback()
//     }
//   }



// const _validateURL = (rule, value, callback) => {
//   const { t }  = i18n.global;
//   const urlPatten = /^(https?|ftp):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/
//   if (urlPatten.test(value) == false) {
//         callback(new Error(t("validation.msgURL")));
//   } else {
//         callback()
//   }
// }

// const _validateURL_http = (rule, value, callback) => {
//   console.log('http == ', value)
//   const { t }  = i18n.global;
//   const urlPatten = /^(http):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/
//   if (urlPatten.test(value) == false) {
//         callback(new Error(t("validation.msgURL")));
//   } else {
//         callback()
//   }
// }

// const _validateURL_https = (rule, value, callback) => {
//   console.log('https == ', value)
//   const { t }  = i18n.global;
//   const urlPatten = /^(https):\/\/([a-zA-Z0-9.-]+(:[a-zA-Z0-9.&%$-]+)*@)*((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9][0-9]?)(\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}|([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+\.(com|edu|gov|int|mil|net|org|biz|arpa|info|name|pro|aero|coop|museum|[a-zA-Z]{2}))(:[0-9]+)*(\/($|[a-zA-Z0-9.,?'\\+&%$#=~_-]+))*$/
//   if (urlPatten.test(value) == false) {
//         callback(new Error(t("validation.msgURL")));
//   } else {
//         callback()
//   }
// }

// export {
//     _validateName,
//     _validateLength,
//     _validateIp,
//     _validateEmail,
//     _validateUserPassword,
//     _validateUserLengthPassword,
//     _validatePackageName,
//     _validateSelect,
//     _validateSelect2,
//     _validateURL,
//     _validateURL_http,
//     _validateURL_https,
// 	_valdateLimitLength,
// 	_validateAlphanumericUnderbarHyphen,
//   _validateAlphanumericUnderbarHyphenSpace,
// 	_validateAlphaNumeric,
// 	_validateAlphaNumericHyphen,
// 	_validateDomain,
// 	_validateOpenShiftAppName,
// 	_validateAppName
// }
