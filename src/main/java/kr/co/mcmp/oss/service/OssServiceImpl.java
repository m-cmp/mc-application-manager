package kr.co.mcmp.oss.service;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.oss.entity.Oss;
import kr.co.mcmp.oss.nexus.service.NexusService;
import kr.co.mcmp.oss.repository.OssRepository;
import kr.co.mcmp.oss.repository.OssTypeRepository;
import kr.co.mcmp.util.AES256Utils;

import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class OssServiceImpl implements OssService {

	private final OssRepository ossRepository;

	private final OssTypeRepository ossTypeRepository;

	private final NexusService nexusService;

	/**
	 * OSS 목록 조회
	 * @return List<OssDto> ossDtoList
	 */
	@Override
	public List<OssDto> getAllOssList() {
		List<OssDto> ossList = ossRepository.findAll()
				.stream()
				.map(OssDto::from)
				.collect(Collectors.toList());

		if ( !CollectionUtils.isEmpty(ossList) ) {
			ossList = ossList.stream()
					.map(ossDto -> OssDto.withModifiedEncriptPassword(ossDto, encodingBase64String(decryptAesString(ossDto.getOssPassword()))))
					.collect(Collectors.toList());
		}

		return ossList;
	}

	/**
	 * OSS 목록 조회
	 * @param ossTypeName
	 * @return List<OssDto> ossDtoList
	 */
	@Override
	public List<OssDto> getOssList(String ossTypeName) {
		List<OssTypeDto> ossTypeList = ossTypeRepository.findByOssTypeName(ossTypeName)
				.stream()
				.map(OssTypeDto::from)
				.collect(Collectors.toList());
		log.info(ossTypeList);

		// ossTypeList에서 ossTypeIdx 목록을 추출
		List<Long> ossTypeIdxList = ossTypeList.stream()
				.map(OssTypeDto::getOssTypeIdx)
				.collect(Collectors.toList());

		List<OssDto> ossList = ossRepository.findByOssTypeIdxIn(ossTypeIdxList)
				.stream()
				.map(OssDto::from)
				.collect(Collectors.toList());

		if ( !CollectionUtils.isEmpty(ossList) ) {
			ossList = ossList
					.stream()
					.map(ossDto -> OssDto.withModifiedEncriptPassword(ossDto, encodingBase64String(decryptAesString(ossDto.getOssPassword()))))
					.collect(Collectors.toList());
		}

		return ossList;
	}

	/**
	 * OSS 등록
	 * @param ossDto
	 * @return
	 */
	@Transactional
	@Override
	public Long registOss(OssDto ossDto) {
		OssTypeDto ossTypeDto = OssTypeDto.from(ossTypeRepository.findByOssTypeIdx(ossDto.getOssTypeIdx()));
		ossDto = ossDto.withModifiedEncriptPassword(ossDto, encryptAesString(ossDto.getOssPassword()));
		ossDto = OssDto.from(ossRepository.save(OssDto.toEntity(ossDto, ossTypeDto)));
		return ossDto.getOssIdx();
	}

	/**
	 * OSS 수정
	 * @param ossDto
	 * @return
	 */
	@Override
	public Long updateOss(OssDto ossDto) {
		OssTypeDto ossTypeDto = OssTypeDto.from(ossTypeRepository.findByOssTypeIdx(ossDto.getOssTypeIdx()));

		ossDto = ossDto.withModifiedEncriptPassword(ossDto, encryptAesString(ossDto.getOssPassword()));
		ossRepository.save(OssDto.toEntity(ossDto, ossTypeDto));
		return ossDto.getOssIdx();
	}

	/**
	 * OSS 삭제
	 * @param ossIdx
	 */
	@Transactional
	@Override
	public Boolean deleteOss(Long ossIdx) {
		try {
			OssDto deleteOss = OssDto.from(ossRepository.findByOssIdx(ossIdx));
			ossRepository.deleteByOssIdx(ossIdx);
			return true;
		} catch (EmptyResultDataAccessException e) {
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * OSS 연결 확인
	 * @param ossDto
	 * TODO : 추후 OSS 추가
	 */
	@Transactional
	@Override
	public Boolean checkConnection(OssDto ossDto) {
		OssTypeDto osstypeDto = OssTypeDto.from(ossTypeRepository.findByOssTypeIdx(ossDto.getOssTypeIdx()));

		if(!osstypeDto.getOssTypeName().isEmpty()) {
			switch(osstypeDto.getOssTypeName()) {
				case "NEXUS" :
					if (StringUtils.isBlank(ossDto.getOssUrl()) ||
							StringUtils.isBlank(ossDto.getOssUsername()) ) {
						log.error("접속정보 누락");
						return false;
					}

					// Front에서 Base64Encoding한 데이터를 복호화하여 AES256 암호화 함.
					ossDto.withModifiedEncriptPassword(ossDto, encryptAesString(ossDto.getOssPassword()));
					return nexusService.checkNexusConnection(ossDto);

				default:
					log.debug("[checkConnection] oss code >>> {}", osstypeDto.getOssTypeName());
					log.error("Code is not registered] ossTypeName >>> {}", osstypeDto.getOssTypeName());
					return false;
			}
		}
		else {
			log.debug("[checkConnection] oss code >>> {}", osstypeDto.getOssTypeName());
			log.error("[OssTypeName is Null] ossTypeIdx >>> {}", ossDto.getOssTypeIdx());
			return false;
		}
	}

	/**
	 * OSS 정보 상세 조회
	 * @param ossIdx
	 * @return
	 */
	public OssDto detailOss(Long ossIdx) {
		Oss oss = ossRepository.findByOssIdx(ossIdx);
		return OssDto.withDetailDecryptPassword(oss, encodingBase64String(decryptAesString(oss.getOssPassword())));
	}

	public OssDto detailOssByOssName(String ossName) {
		Oss oss = ossRepository.findByOssName(ossName);
		String pwd = oss.getOssPassword();
		String decodePwd = Base64Utils.base64Decoding(pwd);
		return OssDto.builder()
				.ossIdx(oss.getOssIdx())
				.ossTypeIdx(oss.getOssType().getOssTypeIdx())
				.ossName(oss.getOssName())
				.ossDesc(oss.getOssDesc())
				.ossUrl(oss.getOssUrl())
				.ossUsername(oss.getOssUsername())
				.ossPassword(decodePwd)
				.build();
	}

//	/**
//	 * OSS 정보 상세 조회
//	 * @param ossCd
//	 * @return
//	 */
//	public OssDto getOssByOssCd(String ossCd) {
//		return OssDto.from(ossRepository.findByOssType_OssTypeName(ossCd));
//	}

	/**
	 * OSS 정보 중복 체크(ossName, ossUrl, ossUsername)
	 * @param ossDto
	 * 중복: true / 아니면 false
	 * @return
	 */
	public Boolean isOssInfoDuplicated(OssDto ossDto) {
		return ossRepository.existsByOssNameAndOssUrlAndOssUsername(
				ossDto.getOssName(),
				ossDto.getOssUrl(),
				ossDto.getOssUsername());
	}

	/**
	 * 패스워드 암호화 (Front로 Base64Encoding한 데이터를 보냄.)
	 * @param str
	 * @return
	 */
	public String encryptBase64String(String str) {
		if ( StringUtils.isNotBlank(str) ) {
			return Base64Utils.base64Encoding(AES256Utils.encrypt(str));
		}
		else {
			return null;
		}
	}

	/**
	 * 패스워드 암호화 (Front에서 Base64Encoding한 데이터를 복호화하여 AES256 암호화 함.)
	 * @param str
	 * @return
	 */
	public String encryptAesString(String str) {
		if ( StringUtils.isNotBlank(str) ) {
			return AES256Utils.encrypt(Base64Utils.base64Decoding(str));
		}
		else {
			return null;
		}
	}
	/**
	 * 패스워드/토큰 암호화 (Front로 Base64Encoding한 데이터를 보내.)
	 * @param str
	 * @return
	 */
	public String encodingBase64String(String str) {
		if ( StringUtils.isNotBlank(str) ) {
			return Base64Utils.base64Encoding(str);
		}
		else {
			return null;
		}
	}

	/**
	 * 패스워드 복호화
	 * @param encryptedStr
	 * @return
	 */
	public String decryptAesString(String encryptedStr) {
		if (StringUtils.isNotBlank(encryptedStr)) {
			// AES256으로 암호화된 문자열을 복호화
			String decrypted = AES256Utils.decrypt(encryptedStr);
			// 복호화된 문자열을 Base64로 인코딩
			return decrypted;
		} else {
			return null;
		}
	}
}